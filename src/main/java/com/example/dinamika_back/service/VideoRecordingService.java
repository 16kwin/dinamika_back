package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.VideoEventRequest;
import com.example.dinamika_back.dto.VideoRecordDTO;
import com.example.dinamika_back.features.AccessAudit;
import com.example.dinamika_back.features.ConditionalOnFeature;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.FeatureService;
import com.example.dinamika_back.model.Station;
import com.example.dinamika_back.model.VideoCamera;
import com.example.dinamika_back.model.VideoRecord;
import com.example.dinamika_back.model.VideoRecordFile;
import com.example.dinamika_back.repository.StationRepository;
import com.example.dinamika_back.repository.VideoCameraRepository;
import com.example.dinamika_back.repository.VideoRecordFileRepository;
import com.example.dinamika_back.repository.VideoRecordRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Запись видео по событиям станции.
 *
 * <p>Событие «начало» запускает по процессу FFmpeg на каждую камеру станции, событие «окончание»
 * останавливает их, проверяет файлы и закрывает строку журнала. Имя файла — по схеме видеоархива:
 * {@code ДатаВремя_ТипСобытия_UidЗаписи_ИмяКамеры_IpКамеры_UidСтанции.mp4}.
 *
 * <p>Сторож: событие требует {@code video.recording}; начало записи — в журнал доступа. Бина нет, когда потолок
 * выключает видео (уровень L3): тогда не выполняется и закрытие прерванных записей при старте.
 */
@Slf4j
@Service
@ConditionalOnFeature(Feature.VIDEO)
@RequiredArgsConstructor
public class VideoRecordingService {

    public static final String PHASE_START = "start";
    public static final String PHASE_STOP = "stop";

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DAY_FOLDER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** Сколько ждать, пока FFmpeg корректно закроет файл после «q» */
    private static final long STOP_GRACE_MILLIS = 15_000;

    private final VideoSettings settings;
    private final FfmpegRunner ffmpeg;
    private final VideoCameraRepository cameraRepository;
    private final VideoRecordRepository recordRepository;
    private final VideoRecordFileRepository fileRepository;
    private final StationRepository stationRepository;
    private final VideoMapper mapper;
    private final FeatureService features;
    private final AccessAudit audit;

    /** Идущие записи: id записи → процессы её камер */
    private final Map<Long, ActiveRecord> active = new ConcurrentHashMap<>();

    /** Процессы одной записи. Поля starting и closing меняются только под блокировкой самого объекта */
    private static final class ActiveRecord {
        final Long recordId;
        final LocalDateTime startedAt;
        /** id файла → процесс камеры */
        final Map<Long, RecorderProcess> processes = new ConcurrentHashMap<>();
        /** id файла → id камеры, для признака «камера пишет» */
        final Map<Long, Long> cameraByFile = new ConcurrentHashMap<>();
        boolean starting = true;
        boolean closing;

        ActiveRecord(Long recordId, LocalDateTime startedAt) {
            this.recordId = recordId;
            this.startedAt = startedAt;
        }
    }

    // ==================== Жизненный цикл ====================

    /** После перезапуска сервера процессов прошлых записей нет — такие записи закрываются как прерванные */
    @PostConstruct
    void recoverInterrupted() {
        String message = "Запись прервана: сервер был перезапущен";
        LocalDateTime now = LocalDateTime.now();
        for (VideoRecord record : recordRepository.findByStatus(VideoRecord.STATUS_RECORDING)) {
            record.setStatus(VideoRecord.STATUS_FAILED);
            record.setFinishedAt(now);
            record.setErrorMessage(message);
            recordRepository.save(record);
        }
        for (VideoRecordFile file : fileRepository.findByStatus(VideoRecord.STATUS_RECORDING)) {
            file.setStatus(VideoRecord.STATUS_FAILED);
            file.setErrorMessage(message);
            fileRepository.save(file);
        }
    }

    /** При остановке сервера идущие записи корректно закрываются, чтобы файлы остались читаемыми */
    @PreDestroy
    void stopAllOnShutdown() {
        for (Long recordId : new ArrayList<>(active.keySet())) {
            try {
                closeRecord(recordId);
            } catch (RuntimeException e) {
                log.warn("Не удалось закрыть запись {} при остановке сервера: {}", recordId, e.getMessage());
            }
        }
    }

    // ==================== События ====================

    /** Событие станции: «начало» запускает запись, «окончание» останавливает. Без записи по событиям — 404 */
    public synchronized VideoRecordDTO handleEvent(VideoEventRequest request) {
        features.require(Feature.VIDEO_RECORDING);
        if (request == null) throw VideoException.badRequest("Тело запроса обязательно");
        if (request.getStationId() == null) throw VideoException.badRequest("Не указана станция (stationId)");
        VideoEventType type = VideoEventType.fromCode(request.getEventType()).orElseThrow(() ->
                VideoException.badRequest("Неизвестное событие: '" + request.getEventType()
                        + "'. Допустимо: authority, issue"));
        String phase = request.getPhase() == null ? "" : request.getPhase().trim().toLowerCase();
        Station station = stationRepository.findById(request.getStationId())
                .orElseThrow(() -> VideoException.notFound("Станция " + request.getStationId() + " не найдена"));

        return switch (phase) {
            case PHASE_START -> start(station, type);
            case PHASE_STOP -> stop(station, type);
            default -> throw VideoException.badRequest("Неизвестная фаза события: '" + request.getPhase()
                    + "'. Допустимо: start, stop");
        };
    }

    private VideoRecordDTO start(Station station, VideoEventType type) {
        if (recordRepository.findFirstByStationIdAndEventTypeAndStatusOrderByStartedAtDesc(
                station.getId(), type.code(), VideoRecord.STATUS_RECORDING).isPresent()) {
            throw VideoException.conflict("Запись события «" + type.title() + "» на станции «"
                    + station.getName() + "» уже идёт");
        }
        List<VideoCamera> cameras = cameraRepository.findByStationIdOrderBySortOrderAscIdAsc(station.getId());
        if (cameras.isEmpty()) {
            throw VideoException.badRequest("К станции «" + station.getName() + "» не привязаны камеры");
        }
        // Версия из кэша: `ffmpeg -version` не запускается на каждое событие
        if (ffmpeg.cachedVersion() == null) {
            throw VideoException.unavailable("FFmpeg не найден: " + settings.ffmpegPath()
                    + ". Укажите путь в video.ffmpeg-path");
        }

        LocalDateTime now = LocalDateTime.now();
        VideoRecord record = new VideoRecord();
        record.setEventType(type.code());
        record.setStationId(station.getId());
        record.setStatus(VideoRecord.STATUS_RECORDING);
        record.setStartedAt(now);
        record = recordRepository.save(record);

        ActiveRecord activeRecord = new ActiveRecord(record.getId(), now);
        active.put(record.getId(), activeRecord);

        int started = 0;
        for (VideoCamera camera : cameras) {
            String fileName = String.join("_",
                    now.format(FILE_TIMESTAMP),
                    type.fileTag(),
                    record.getUid().toString(),
                    safe(camera.getName()),
                    safe(camera.getIp()),
                    safe(station.getUid())) + ".mp4";
            String relative = now.format(DAY_FOLDER) + "/" + fileName;

            VideoRecordFile file = new VideoRecordFile();
            file.setRecordId(record.getId());
            file.setCameraId(camera.getId());
            file.setCameraName(camera.getName());
            file.setCameraIp(camera.getIp());
            file.setCameraType(camera.getCameraType());
            file.setFilePath(relative);
            file.setStatus(VideoRecord.STATUS_RECORDING);
            file = fileRepository.save(file);

            Path target = settings.storageRoot().resolve(relative);
            try {
                RecorderProcess process = RecorderProcess.start(ffmpeg.recordCommand(camera.getRtspUrl(), target), target);
                Long fileId = file.getId();
                activeRecord.processes.put(fileId, process);
                activeRecord.cameraByFile.put(fileId, camera.getId());
                started++;
                process.onExit().thenRun(() -> onProcessExit(activeRecord, fileId, process));
                log.info("Видео: старт записи {} камеры {} ({}) → {}", record.getId(), camera.getName(),
                        VideoSource.maskPassword(camera.getRtspUrl()), target);
            } catch (IOException e) {
                file.setStatus(VideoRecord.STATUS_FAILED);
                file.setErrorMessage("Не удалось запустить FFmpeg: " + e.getMessage());
                fileRepository.save(file);
            } catch (VideoException e) {
                // Источник камеры не прошёл проверку политики установки — FFmpeg этой камеры не запускается
                file.setStatus(VideoRecord.STATUS_FAILED);
                file.setErrorMessage(e.getMessage());
                fileRepository.save(file);
            }
        }

        // Если все камеры уже отвалились (или не стартовали) — запись закрывается сразу
        synchronized (activeRecord) {
            activeRecord.starting = false;
            if (activeRecord.processes.isEmpty() && !activeRecord.closing) {
                activeRecord.closing = true;
                finalizeRecord(activeRecord.recordId, activeRecord.startedAt);
                active.remove(activeRecord.recordId);
            }
        }
        try {
            audit.record(AccessAudit.Event.VIDEO_RECORDING_STARTED, Feature.VIDEO_RECORDING, String.valueOf(record.getId()),
                    Map.of("station", String.valueOf(station.getUid()), "event", type.code(),
                            "cameras", cameras.size(), "started", started));
        } catch (RuntimeException e) {
            // Запись уже идёт и есть в журнале видеоархива: сбой журнала доступа её не обрывает, но виден в логе
            log.warn("Видео: начало записи {} не попало в журнал доступа: {}", record.getId(), e.getMessage());
        }
        return toDto(record.getId());
    }

    private VideoRecordDTO stop(Station station, VideoEventType type) {
        VideoRecord record = recordRepository.findFirstByStationIdAndEventTypeAndStatusOrderByStartedAtDesc(
                        station.getId(), type.code(), VideoRecord.STATUS_RECORDING)
                .orElseThrow(() -> VideoException.conflict("Нет идущей записи события «" + type.title()
                        + "» на станции «" + station.getName() + "»"));
        closeRecord(record.getId());
        return toDto(record.getId());
    }

    /** Остановить все процессы записи, проверить файлы и закрыть строку журнала */
    private void closeRecord(Long recordId) {
        ActiveRecord activeRecord = active.get(recordId);
        if (activeRecord == null) {
            // Строка открыта, а процессов нет — закрываем как неудачную
            recordRepository.findById(recordId).ifPresent(record -> {
                if (VideoRecord.STATUS_RECORDING.equals(record.getStatus())) {
                    record.setStatus(VideoRecord.STATUS_FAILED);
                    record.setFinishedAt(LocalDateTime.now());
                    record.setErrorMessage("Процесс записи не найден");
                    recordRepository.save(record);
                }
            });
            return;
        }
        synchronized (activeRecord) {
            if (activeRecord.closing) return;
            activeRecord.closing = true;
        }

        // Камеры останавливаются параллельно: каждая может закрывать файл до нескольких секунд
        List<CompletableFuture<Void>> stops = activeRecord.processes.values().stream()
                .map(process -> CompletableFuture.runAsync(() -> process.stop(STOP_GRACE_MILLIS)))
                .toList();
        CompletableFuture.allOf(stops.toArray(CompletableFuture[]::new)).join();

        activeRecord.processes.forEach((fileId, process) ->
                fileRepository.findById(fileId).ifPresent(file -> finalizeFile(file, process, true)));
        finalizeRecord(recordId, activeRecord.startedAt);
        active.remove(recordId);
        log.info("Видео: запись {} остановлена", recordId);
    }

    /** Процесс завершился сам: ошибка камеры или достигнут предел длительности */
    private void onProcessExit(ActiveRecord activeRecord, Long fileId, RecorderProcess process) {
        synchronized (activeRecord) {
            if (activeRecord.closing || process.stopRequested()) return;
            fileRepository.findById(fileId).ifPresent(file -> {
                finalizeFile(file, process, false);
                log.warn("Видео: камера {} перестала писать до окончания события {}: {}", file.getCameraName(),
                        activeRecord.recordId, Objects.requireNonNullElse(file.getErrorMessage(), "файл короче события"));
            });
            activeRecord.processes.remove(fileId);
            activeRecord.cameraByFile.remove(fileId);
            if (activeRecord.processes.isEmpty() && !activeRecord.starting) {
                activeRecord.closing = true;
                finalizeRecord(activeRecord.recordId, activeRecord.startedAt);
                active.remove(activeRecord.recordId);
            }
        }
    }

    /** Итог по файлу камеры: размер и длительность — или причина, по которой файла нет */
    private void finalizeFile(VideoRecordFile file, RecorderProcess process, boolean expectedStop) {
        Path path = settings.storageRoot().resolve(file.getFilePath());
        long size = 0;
        try {
            if (Files.exists(path)) size = Files.size(path);
        } catch (IOException ignored) {
            // размер не прочитан — файл считаем пустым
        }
        int exitCode = process.exitCode();
        if (size > 0 && (expectedStop || exitCode == 0)) {
            BigDecimal duration = ffmpeg.durationOf(path);
            if (duration != null) {
                file.setStatus(VideoRecord.STATUS_DONE);
                file.setFileSize(size);
                file.setDurationSeconds(duration);
                // Файл читается, но FFmpeg закончил сам раньше предела длительности — камера оборвала поток
                boolean interrupted = !expectedStop
                        && duration.compareTo(BigDecimal.valueOf(settings.maxRecordSeconds() - 1L)) < 0;
                String reason = process.errorText();
                file.setErrorMessage(!interrupted ? null
                        : "Поток прервался через " + duration.setScale(0, RoundingMode.HALF_UP) + " с"
                        + (reason.isBlank() ? "" : ". " + FfmpegRunner.explain(reason)));
            } else {
                file.setStatus(VideoRecord.STATUS_FAILED);
                file.setFileSize(size);
                file.setErrorMessage("Файл записан не полностью и не читается");
            }
        } else {
            String reason = process.errorText();
            file.setStatus(VideoRecord.STATUS_FAILED);
            file.setFileSize(size);
            file.setErrorMessage(reason.isBlank()
                    ? "Камера не передала ни одного кадра"
                    : FfmpegRunner.explain(reason));
        }
        fileRepository.save(file);
    }

    /** Итог по записи: длительность — по самому длинному файлу, статус — есть ли хоть один файл */
    private void finalizeRecord(Long recordId, LocalDateTime startedAt) {
        recordRepository.findById(recordId).ifPresent(record -> {
            List<VideoRecordFile> files = fileRepository.findByRecordIdOrderByIdAsc(recordId);
            LocalDateTime now = LocalDateTime.now();
            List<VideoRecordFile> done = files.stream()
                    .filter(file -> VideoRecord.STATUS_DONE.equals(file.getStatus())).toList();
            // В причину попадают и файлы без ошибки FFmpeg, но короче события
            List<VideoRecordFile> problems = files.stream()
                    .filter(file -> !VideoRecord.STATUS_DONE.equals(file.getStatus()) || file.getErrorMessage() != null)
                    .toList();

            BigDecimal duration = done.stream()
                    .map(VideoRecordFile::getDurationSeconds)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(BigDecimal.valueOf(Duration.between(startedAt, now).toMillis())
                            .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP));

            record.setFinishedAt(now);
            record.setDurationSeconds(duration);
            record.setStatus(done.isEmpty() ? VideoRecord.STATUS_FAILED : VideoRecord.STATUS_DONE);
            record.setErrorMessage(problems.isEmpty() ? null : problems.stream()
                    .map(file -> file.getCameraName() + ": " + Objects.requireNonNullElse(file.getErrorMessage(), "нет файла"))
                    .reduce((a, b) -> a + "; " + b).orElse(null));
            recordRepository.save(record);
        });
    }

    // ==================== Состояние ====================

    /** Идущие записи с файлами — для отметки REC на странице */
    public List<VideoRecordDTO> activeRecords() {
        List<VideoRecordDTO> result = new ArrayList<>();
        for (Long recordId : active.keySet()) {
            VideoRecordDTO dto = toDto(recordId);
            if (dto != null) result.add(dto);
        }
        result.sort(Comparator.comparing(VideoRecordDTO::getStartedAt).reversed());
        return result;
    }

    /** Камеры, которые пишут прямо сейчас */
    public Set<Long> recordingCameraIds() {
        Set<Long> ids = new HashSet<>();
        for (ActiveRecord activeRecord : active.values()) {
            for (Map.Entry<Long, RecorderProcess> entry : activeRecord.processes.entrySet()) {
                if (entry.getValue().isAlive()) {
                    Long cameraId = activeRecord.cameraByFile.get(entry.getKey());
                    if (cameraId != null) ids.add(cameraId);
                }
            }
        }
        return ids;
    }

    private VideoRecordDTO toDto(Long recordId) {
        return recordRepository.findById(recordId)
                .map(record -> mapper.toDto(record, fileRepository.findByRecordIdOrderByIdAsc(recordId),
                        mapper.stationNames(List.of(Objects.requireNonNullElse(record.getStationId(), -1L)))))
                .orElse(null);
    }

    /** Часть имени файла: без символов, запрещённых в именах файлов Windows */
    static String safe(String value) {
        if (value == null || value.isBlank()) return "none";
        return value.trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
    }
}

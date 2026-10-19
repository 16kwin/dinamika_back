package com.example.dinamika_back.service;

import com.example.dinamika_back.config.security.AppRoles;
import com.example.dinamika_back.dto.VideoRecordDTO;
import com.example.dinamika_back.dto.VideoStatusDTO;
import com.example.dinamika_back.features.ConditionalOnFeature;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.FeatureService;
import com.example.dinamika_back.model.VideoRecord;
import com.example.dinamika_back.model.VideoRecordFile;
import com.example.dinamika_back.repository.VideoRecordFileRepository;
import com.example.dinamika_back.repository.VideoRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Журнал видеоархива: список записей, карточка записи, путь к файлу для отдачи и состояние модуля.
 * Бина нет, когда потолок выключает видео (уровень L3).
 */
@Service
@ConditionalOnFeature(Feature.VIDEO)
@RequiredArgsConstructor
public class VideoArchiveService {

    /** Нижняя граница периода, если он не задан */
    private static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);

    /** Функции, которым нужен FFmpeg. Без них (включён только журнал) состояние FFmpeg не запускает вовсе */
    private static final List<Feature> FFMPEG_FEATURES =
            List.of(Feature.VIDEO_LIVE, Feature.VIDEO_CLIPS, Feature.VIDEO_RECORDING, Feature.VIDEO_CAMERAS);

    private final VideoRecordRepository recordRepository;
    private final VideoRecordFileRepository fileRepository;
    private final VideoRecordingService recordingService;
    private final FfmpegRunner ffmpeg;
    private final VideoSettings settings;
    private final VideoMapper mapper;
    private final FeatureService features;

    /**
     * Записи за период [from, to] включительно, свежие сверху. Станция и событие — необязательные фильтры.
     */
    public List<VideoRecordDTO> list(LocalDate from, LocalDate to, Long stationId, String eventType) {
        LocalDate dateFrom = from != null ? from : EARLIEST;
        LocalDate dateTo = to != null ? to : LocalDate.now();
        if (dateFrom.isAfter(dateTo)) throw VideoException.badRequest("Дата from не может быть позже даты to");

        String eventFilter = eventType == null || eventType.isBlank() ? null : eventType.trim().toLowerCase();
        List<VideoRecord> records = recordRepository.findByPeriod(dateFrom.atStartOfDay(), dateTo.plusDays(1).atStartOfDay())
                .stream()
                .filter(record -> stationId == null || stationId.equals(record.getStationId()))
                .filter(record -> eventFilter == null || eventFilter.equals(record.getEventType()))
                .toList();
        if (records.isEmpty()) return List.of();

        Map<Long, List<VideoRecordFile>> filesByRecord = fileRepository
                .findByRecordIdIn(records.stream().map(VideoRecord::getId).toList())
                .stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.groupingBy(VideoRecordFile::getRecordId));
        Map<Long, String> stationNames = mapper.stationNames(records.stream().map(VideoRecord::getStationId).toList());

        return records.stream()
                .map(record -> mapper.toDto(record, filesByRecord.getOrDefault(record.getId(), List.of()), stationNames))
                .toList();
    }

    public VideoRecordDTO get(Long id) {
        VideoRecord record = recordRepository.findById(id)
                .orElseThrow(() -> VideoException.notFound("Видеозапись " + id + " не найдена"));
        return mapper.toDto(record, fileRepository.findByRecordIdOrderByIdAsc(id),
                mapper.stationNames(List.of(record.getStationId() == null ? -1L : record.getStationId())));
    }

    /** Файл записи для отдачи. Путь не может выйти за каталог видеоархива */
    public ResolvedFile resolveFile(Long fileId) {
        VideoRecordFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> VideoException.notFound("Файл видеозаписи " + fileId + " не найден"));
        if (VideoRecord.STATUS_RECORDING.equals(file.getStatus())) {
            throw VideoException.conflict("Запись ещё идёт — файл будет доступен после окончания события");
        }
        Path root = settings.storageRoot();
        Path path = root.resolve(file.getFilePath()).normalize();
        if (!path.startsWith(root)) throw VideoException.badRequest("Недопустимый путь файла");
        if (!Files.isRegularFile(path)) {
            throw VideoException.notFound("Файл видеозаписи отсутствует на диске: " + file.getFilePath());
        }
        return new ResolvedFile(path, path.getFileName().toString());
    }

    /**
     * Состояние модуля. Страница опрашивает его постоянно — версия FFmpeg берётся из кэша: {@code ffmpeg -version}
     * запускается один раз, и только если включена функция, которой FFmpeg нужен (живой просмотр, клипы, запись по
     * событиям, проверка камер). Включён только журнал — FFmpeg не запускается, {@code ffmpegAvailable=false}.
     * Абсолютный путь архива — только руководителям; остальным {@code storageDir=null}
     */
    public VideoStatusDTO status() {
        String version = FFMPEG_FEATURES.stream().anyMatch(features::isOn) ? ffmpeg.cachedVersion() : null;
        String storageDir = AppRoles.currentIsManager() ? settings.storageRoot().toString() : null;
        return new VideoStatusDTO(version != null, version, storageDir, recordingService.activeRecords());
    }

    /** Найденный на диске файл и имя для скачивания */
    public record ResolvedFile(Path path, String fileName) {
    }
}

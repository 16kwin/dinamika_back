package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.VideoCameraDTO;
import com.example.dinamika_back.dto.VideoCameraUpdateDTO;
import com.example.dinamika_back.dto.VideoStreamCheckDTO;
import com.example.dinamika_back.features.AccessAudit;
import com.example.dinamika_back.features.ConditionalOnFeature;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.FeatureService;
import com.example.dinamika_back.model.Station;
import com.example.dinamika_back.model.VideoCamera;
import com.example.dinamika_back.repository.StationRepository;
import com.example.dinamika_back.repository.VideoCameraRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Камеры видеоархива: список и правка со страницы, кадр с камеры, проверка потока.
 * Живой просмотр — в {@link LiveStreamService}.
 *
 * <p>Сторож: правка и проверка камеры требуют {@code video.cameras}, кадр — {@code video.live}. Смена адреса
 * камеры и показанный кадр пишутся в журнал доступа (адреса — без паролей). Бина нет без видео (уровень L3).
 */
@Service
@ConditionalOnFeature(Feature.VIDEO)
@RequiredArgsConstructor
public class VideoCameraService {

    private final VideoCameraRepository cameraRepository;
    private final StationRepository stationRepository;
    private final VideoRecordingService recordingService;
    private final LiveStreamService liveService;
    private final FfmpegRunner ffmpeg;
    private final VideoSettings settings;
    private final VideoMapper mapper;
    private final FeatureService features;
    private final AccessAudit audit;

    /** Последний кадр камеры или последняя ошибка — чтобы опрос со страницы не запускал FFmpeg на каждый запрос */
    private record CachedFrame(byte[] jpeg, VideoException error, long takenAt) {
    }

    private final Map<Long, CachedFrame> frames = new ConcurrentHashMap<>();
    private final Map<Long, Object> frameLocks = new ConcurrentHashMap<>();

    /** Камеры; stationUid — только камеры станции, для окна «Видео» на панели станций */
    public List<VideoCameraDTO> list(String stationUid) {
        List<VideoCamera> cameras = cameraRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, Station> stations = mapper.stations(cameras.stream().map(VideoCamera::getStationId).toList());
        Set<Long> recording = recordingService.recordingCameraIds();
        return cameras.stream()
                .map(camera -> mapper.toDto(camera, stations, recording))
                .filter(camera -> stationUid == null || stationUid.isBlank() || stationUid.trim().equals(camera.getStationUid()))
                .toList();
    }

    /** Правка камеры. Пустые поля запроса не меняют значения */
    @Transactional
    public VideoCameraDTO update(Long id, VideoCameraUpdateDTO request) {
        features.require(Feature.VIDEO_CAMERAS);
        if (request == null) throw VideoException.badRequest("Тело запроса обязательно");
        VideoCamera camera = find(id);
        String urlBefore = camera.getRtspUrl();
        String ipBefore = camera.getIp();

        if (request.getName() != null) {
            String name = request.getName().trim();
            if (name.isEmpty()) throw VideoException.badRequest("Наименование камеры не может быть пустым");
            if (name.length() > 64) throw VideoException.badRequest("Наименование камеры длиннее 64 символов");
            camera.setName(name);
        }
        if (request.getIp() != null) {
            String ip = request.getIp().trim();
            if (ip.isEmpty()) throw VideoException.badRequest("IP адрес камеры не может быть пустым");
            if (ip.length() > 64) throw VideoException.badRequest("IP адрес камеры длиннее 64 символов");
            camera.setIp(ip);
        }
        if (request.getMac() != null) {
            String mac = request.getMac().trim().toUpperCase();
            if (!mac.isEmpty() && !mac.matches("([0-9A-F]{2}[:-]){5}[0-9A-F]{2}")) {
                throw VideoException.badRequest("MAC адрес должен быть вида 00:1A:2B:3C:4D:5E");
            }
            camera.setMac(mac.isEmpty() ? null : mac.replace('-', ':'));
        }
        if (request.getCameraType() != null) {
            String type = request.getCameraType().trim().toLowerCase();
            if (!VideoCamera.TYPE_STATION.equals(type) && !VideoCamera.TYPE_OVERVIEW.equals(type)) {
                throw VideoException.badRequest("Тип камеры: station или overview");
            }
            camera.setCameraType(type);
        }
        boolean urlChanged = false;
        if (request.getRtspUrl() != null) {
            // Пароль «***» — страница прислала ссылку без изменения пароля, подставляется сохранённый.
            // Если сменились хост, порт или логин — 400: пароль нужно ввести заново
            String url = VideoSource.restorePassword(request.getRtspUrl().trim(), camera.getRtspUrl());
            if (!VideoSource.isRtsp(url) && !VideoSource.isTest(url) && !VideoSource.isFile(url)) {
                throw VideoException.badRequest("URL подключения должен начинаться с rtsp://");
            }
            if (url.length() > 512) throw VideoException.badRequest("URL подключения длиннее 512 символов");
            urlChanged = !url.equals(camera.getRtspUrl());
            // Источник по политике установки: file:/test: и сети камер. При запуске FFmpeg проверяется ещё раз
            if (urlChanged) ffmpeg.verifySource(url);
            camera.setRtspUrl(url);
        }
        if (request.getStationId() != null) {
            if (!stationRepository.existsById(request.getStationId())) {
                throw VideoException.notFound("Станция " + request.getStationId() + " не найдена");
            }
            camera.setStationId(request.getStationId());
        }

        camera = cameraRepository.save(camera);
        // Журнал — до переподключения: сбой журнала откатывает правку, поток остаётся на прежнем адресе
        auditAddressChange(camera, urlBefore, ipBefore);
        frames.remove(id);
        if (urlChanged) liveService.restart(id);
        Map<Long, Station> stations = mapper.stations(List.of(camera.getStationId() == null ? -1L : camera.getStationId()));
        return mapper.toDto(camera, stations, recordingService.recordingCameraIds());
    }

    /** Смена адреса камеры — в журнал доступа: ссылки «было → стало» без паролей, смена пароля — только признаком */
    private void auditAddressChange(VideoCamera camera, String urlBefore, String ipBefore) {
        boolean urlChanged = !Objects.equals(urlBefore, camera.getRtspUrl());
        boolean ipChanged = !Objects.equals(ipBefore, camera.getIp());
        if (!urlChanged && !ipChanged) return;
        Map<String, Object> details = new LinkedHashMap<>();
        if (urlChanged) {
            String maskedBefore = VideoSource.maskPassword(urlBefore);
            String maskedAfter = VideoSource.maskPassword(camera.getRtspUrl());
            details.put("urlBefore", String.valueOf(maskedBefore));
            details.put("urlAfter", String.valueOf(maskedAfter));
            if (Objects.equals(maskedBefore, maskedAfter)) details.put("passwordChanged", true);
        }
        if (ipChanged) {
            details.put("ipBefore", String.valueOf(ipBefore));
            details.put("ipAfter", String.valueOf(camera.getIp()));
        }
        audit.record(AccessAudit.Event.CAMERA_CHANGED, Feature.VIDEO_CAMERAS, String.valueOf(camera.getId()), details);
    }

    /** Кадр JPEG для живого просмотра; одновременные запросы к одной камере ждут один запуск FFmpeg */
    public byte[] snapshot(Long id) {
        features.require(Feature.VIDEO_LIVE);
        byte[] jpeg = frame(id);
        // Кадр с камеры — тот же живой просмотр
        audit.record(AccessAudit.Event.VIDEO_LIVE_OPENED, Feature.VIDEO_LIVE, String.valueOf(id),
                Map.of("mode", "snapshot"));
        return jpeg;
    }

    /** Кадр из кэша или новый запуск FFmpeg; ошибка (с её статусом) тоже держится в кэше */
    private byte[] frame(Long id) {
        VideoCamera camera = find(id);
        Object lock = frameLocks.computeIfAbsent(id, key -> new Object());
        synchronized (lock) {
            CachedFrame cached = frames.get(id);
            long now = System.currentTimeMillis();
            // Ошибку держим вдвое дольше кадра: недоступную камеру незачем дёргать каждую секунду
            long ttl = cached != null && cached.error() != null ? settings.snapshotCacheMillis() * 2 : settings.snapshotCacheMillis();
            if (cached != null && now - cached.takenAt() < ttl) {
                if (cached.error() != null) throw new VideoException(cached.error().status(), cached.error().getMessage());
                return cached.jpeg();
            }
            try {
                byte[] jpeg = ffmpeg.snapshot(camera.getRtspUrl());
                frames.put(id, new CachedFrame(jpeg, null, System.currentTimeMillis()));
                return jpeg;
            } catch (VideoException e) {
                frames.put(id, new CachedFrame(null, e, System.currentTimeMillis()));
                throw e;
            }
        }
    }

    /** Проверка потока — кнопка «Поток» */
    public VideoStreamCheckDTO check(Long id) {
        features.require(Feature.VIDEO_CAMERAS);
        VideoCamera camera = find(id);
        return ffmpeg.check(camera.getId(), camera.getRtspUrl());
    }

    private VideoCamera find(Long id) {
        return cameraRepository.findById(id)
                .orElseThrow(() -> VideoException.notFound("Камера " + id + " не найдена"));
    }
}

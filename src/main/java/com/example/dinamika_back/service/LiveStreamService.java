package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.VideoClipDTO;
import com.example.dinamika_back.dto.VideoLiveStatusDTO;
import com.example.dinamika_back.features.AccessAudit;
import com.example.dinamika_back.features.ConditionalOnFeature;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.FeatureService;
import com.example.dinamika_back.model.VideoCamera;
import com.example.dinamika_back.repository.VideoCameraRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Живой просмотр камер: по одному {@link LiveChannel} на камеру, общий для всех зрителей,
 * и запись с живого потока по кнопке «Записать» — файл потом скачивается на компьютер зрителя.
 *
 * <p>Запись по событиям станции сюда не относится: она идёт своим подключением к камере
 * ({@link VideoRecordingService}) и не зависит от того, смотрит ли кто-то поток.
 *
 * <p>Сторож: подписка требует {@code video.live}, запись по кнопке — {@code video.clips} (второй рубеж за воротами,
 * FFmpeg выключенной подфункции не стартует и при внутреннем вызове). Каждое открытие потока — в журнал доступа.
 * Бина нет, когда потолок выключает видео (уровень L3): нет ни потоков live-*, clip-*, ни планировщика live-idle.
 */
@Slf4j
@Service
@ConditionalOnFeature(Feature.VIDEO)
@RequiredArgsConstructor
public class LiveStreamService {

    /** Ошибка запуска держится столько, чтобы повторные запросы не дёргали недоступную камеру */
    private static final long FAILURE_CACHE_MILLIS = 3_000;
    private static final DateTimeFormatter CLIP_NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final long CLIP_KEEP_MILLIS = TimeUnit.HOURS.toMillis(2);

    private final VideoCameraRepository cameraRepository;
    private final FfmpegRunner ffmpeg;
    private final VideoSettings settings;
    private final FeatureService features;
    private final AccessAudit audit;

    private final Map<Long, LiveChannel> channels = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledFuture<?>> idleStops = new ConcurrentHashMap<>();
    private final Map<Long, Failure> failures = new ConcurrentHashMap<>();
    private final Map<String, Clip> clips = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "live-idle");
        thread.setDaemon(true);
        return thread;
    });

    private record Failure(String message, long at) {
    }

    // ==================== Зрители ====================

    /**
     * Подписка на живой поток камеры. Первый зритель подключает камеру, остальные получают тот же поток.
     * Бросает {@link VideoException} 503, если камера не ответила; 404, если живой просмотр выключен.
     */
    public LiveSubscription subscribe(Long cameraId) {
        features.require(Feature.VIDEO_LIVE);
        LiveSubscription subscription = open(cameraId);
        try {
            audit.record(AccessAudit.Event.VIDEO_LIVE_OPENED, Feature.VIDEO_LIVE, String.valueOf(cameraId),
                    Map.of("camera", String.valueOf(subscription.cameraName()), "mode", "live"));
        } catch (RuntimeException e) {
            // Просмотр не записан в журнал — поток не отдаётся, место зрителя освобождается
            subscription.close();
            throw e;
        }
        return subscription;
    }

    /**
     * Подписка для зрителя ({@link #subscribe}) и записи по кнопке ({@link #startClip}); запись в журнал доступа
     * делают они. Сторож повторён здесь: без живого просмотра FFmpeg камеры не запускается ни для кого
     */
    private LiveSubscription open(Long cameraId) {
        features.require(Feature.VIDEO_LIVE);
        // Камера читается в отдельном потоке. В потоке запроса Spring (open-in-view) держит соединение с базой
        // до конца ответа, а ответ живого потока длится, пока зритель смотрит: каждый зритель занимал бы
        // соединение из пула, и при десятке зрителей остальные запросы к базе вставали бы
        VideoCamera camera = CompletableFuture.supplyAsync(() -> cameraRepository.findById(cameraId)).join()
                .orElseThrow(() -> VideoException.notFound("Камера " + cameraId + " не найдена"));
        LiveChannel channel = channelFor(camera);
        if (!channel.awaitReady(settings.connectTimeoutSeconds() + 4L)) {
            String message = channel.failureMessage();
            failures.put(cameraId, new Failure(message, System.currentTimeMillis()));
            channel.close(message);
            throw VideoException.unavailable(message);
        }
        LiveChannel.Subscriber subscriber = channel.subscribe();
        return new LiveSubscription(this, channel, subscriber);
    }

    private synchronized LiveChannel channelFor(VideoCamera camera) {
        Long cameraId = camera.getId();
        ScheduledFuture<?> idleStop = idleStops.remove(cameraId);
        if (idleStop != null) idleStop.cancel(false);

        LiveChannel channel = channels.get(cameraId);
        if (channel != null && !channel.isClosed()) return channel;

        Failure failure = failures.get(cameraId);
        if (failure != null && System.currentTimeMillis() - failure.at() < FAILURE_CACHE_MILLIS) {
            throw VideoException.unavailable(failure.message());
        }
        try {
            channel = LiveChannel.start(cameraId, camera.getName(), ffmpeg.liveCommand(camera.getRtspUrl()),
                    settings.liveQueueFrames(), this::onChannelClosed);
        } catch (IOException e) {
            throw VideoException.unavailable("Не найден FFmpeg: " + settings.ffmpegPath());
        }
        channels.put(cameraId, channel);
        log.info("Видео: живой поток камеры {} подключён ({})", camera.getName(), VideoSource.maskPassword(camera.getRtspUrl()));
        return channel;
    }

    /** Зритель ушёл. Без зрителей камера отключается не сразу — вдруг окно просто переоткрыли */
    void unsubscribe(LiveChannel channel, LiveChannel.Subscriber subscriber) {
        channel.unsubscribe(subscriber);
        if (channel.viewerCount() > 0 || channel.isClosed()) return;
        synchronized (this) {
            if (channels.get(channel.cameraId()) != channel) return;
            ScheduledFuture<?> previous = idleStops.put(channel.cameraId(), scheduler.schedule(() -> {
                if (channel.viewerCount() == 0) channel.close(null);
            }, settings.liveIdleSeconds(), TimeUnit.SECONDS));
            if (previous != null) previous.cancel(false);
        }
    }

    private void onChannelClosed(LiveChannel channel) {
        channels.remove(channel.cameraId(), channel);
        log.info("Видео: живой поток камеры {} отключён", channel.cameraName());
    }

    /** Переподключить камеру — после смены строки подключения. Зрители переподключатся сами */
    public void restart(Long cameraId) {
        failures.remove(cameraId);
        LiveChannel channel = channels.get(cameraId);
        if (channel != null) channel.close("Настройки камеры изменены — переподключение");
    }

    public List<VideoLiveStatusDTO> status() {
        return channels.values().stream()
                .filter(channel -> !channel.isClosed())
                .sorted(Comparator.comparingLong(LiveChannel::cameraId))
                .map(channel -> new VideoLiveStatusDTO(
                        channel.cameraId(),
                        channel.cameraName(),
                        channel.codec(),
                        channel.viewerCount(),
                        Math.round(channel.fps() * 10) / 10.0,
                        Math.round(channel.bitrateMbps() * 100) / 100.0,
                        channel.droppedFrames(),
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(channel.startedAt()), ZoneId.systemDefault())))
                .toList();
    }

    // ==================== Запись по кнопке «Записать» ====================

    /** Запись с живого потока в файл: подписка без зрителя, кадры сразу на диск, с ближайшего опорного кадра */
    private final class Clip {
        final String id = UUID.randomUUID().toString();
        final Long cameraId;
        final String cameraName;
        final LocalDateTime startedAt = LocalDateTime.now();
        final Path raw;
        final LiveSubscription subscription;
        final Thread writer;
        volatile Path result;
        volatile String error;

        Clip(Long cameraId, String cameraName, LiveSubscription subscription) {
            this.cameraId = cameraId;
            this.cameraName = cameraName;
            this.subscription = subscription;
            this.raw = settings.clipsRoot().resolve(id + ".fmp4");
            this.writer = new Thread(this::write, "clip-" + cameraName);
            this.writer.setDaemon(true);
        }

        private void write() {
            long deadline = System.currentTimeMillis() + settings.maxRecordSeconds() * 1000L;
            try (OutputStream out = Files.newOutputStream(raw)) {
                out.write(subscription.init());
                while (System.currentTimeMillis() < deadline) {
                    byte[] fragment = subscription.next(1, TimeUnit.SECONDS);
                    if (fragment == null) continue;
                    if (fragment == LiveChannel.Subscriber.END) break;
                    out.write(fragment);
                }
            } catch (IOException e) {
                error = "Не удалось записать файл: " + e.getMessage();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                subscription.close();
            }
        }

        String fileName() {
            return VideoRecordingService.safe(cameraName) + "_" + startedAt.format(CLIP_NAME_TIME) + ".mp4";
        }
    }

    public VideoClipDTO startClip(Long cameraId) {
        features.require(Feature.VIDEO_CLIPS);
        cleanupClips();
        try {
            Files.createDirectories(settings.clipsRoot());
        } catch (IOException e) {
            throw VideoException.unavailable("Не удалось создать каталог записей: " + e.getMessage());
        }
        LiveSubscription subscription = open(cameraId);
        Clip clip = new Clip(cameraId, subscription.cameraName(), subscription);
        try {
            // Запись по кнопке — тот же живой поток камеры, только в файл
            audit.record(AccessAudit.Event.VIDEO_LIVE_OPENED, Feature.VIDEO_CLIPS, String.valueOf(cameraId),
                    Map.of("camera", String.valueOf(clip.cameraName), "mode", "clip", "clip", clip.id));
        } catch (RuntimeException e) {
            subscription.close();
            throw e;
        }
        clips.put(clip.id, clip);
        clip.writer.start();
        log.info("Видео: запись с живого потока камеры {} начата ({})", clip.cameraName, clip.id);
        return toDto(clip);
    }

    /** Остановить запись и собрать обычный MP4 для скачивания (склейка — процесс FFmpeg, поэтому со сторожем) */
    public VideoClipDTO stopClip(String clipId) {
        features.require(Feature.VIDEO_CLIPS);
        Clip clip = findClip(clipId);
        synchronized (clip) {
            if (clip.result == null) {
                clip.subscription.close();
                try {
                    clip.writer.join(5_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (clip.error != null) throw VideoException.unavailable(clip.error);
                Path target = settings.clipsRoot().resolve(clip.id + ".mp4");
                ffmpeg.remux(clip.raw, target);
                deleteQuietly(clip.raw);
                clip.result = target;
                log.info("Видео: запись с живого потока камеры {} сохранена ({})", clip.cameraName, clip.id);
            }
        }
        return toDto(clip);
    }

    /** Готовый файл записи и имя для скачивания */
    public record ClipFile(Path path, String fileName) {
    }

    public ClipFile clipFile(String clipId) {
        Clip clip = findClip(clipId);
        if (clip.result == null) throw VideoException.conflict("Запись ещё идёт — сначала остановите её");
        return new ClipFile(clip.result, clip.fileName());
    }

    private Clip findClip(String clipId) {
        Clip clip = clips.get(clipId);
        if (clip == null) throw VideoException.notFound("Запись " + clipId + " не найдена");
        return clip;
    }

    private VideoClipDTO toDto(Clip clip) {
        Path result = clip.result;
        Long size = null;
        BigDecimal duration = null;
        if (result != null) {
            try {
                size = Files.size(result);
            } catch (IOException ignored) {
                // размер не прочитан — не страшно
            }
            duration = ffmpeg.durationOf(result);
        }
        return new VideoClipDTO(clip.id, clip.cameraId, clip.cameraName, clip.startedAt,
                result == null ? null : clip.fileName(), size, duration,
                result == null ? null : "/api/video/clips/" + clip.id + "/download");
    }

    /** Готовые записи хранятся пару часов: их скачивают сразу после остановки */
    private void cleanupClips() {
        long now = System.currentTimeMillis();
        clips.values().removeIf(clip -> {
            boolean old = clip.result != null
                    && now - clip.startedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() > CLIP_KEEP_MILLIS;
            if (old) deleteQuietly(clip.result);
            return old;
        });
        // Файлы, оставшиеся после перезапуска сервера
        Path root = settings.clipsRoot();
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> files = Files.list(root)) {
            files.filter(path -> {
                try {
                    return now - Files.getLastModifiedTime(path).toMillis() > CLIP_KEEP_MILLIS;
                } catch (IOException e) {
                    return false;
                }
            }).forEach(LiveStreamService::deleteQuietly);
        } catch (IOException ignored) {
            // каталог недоступен — уберём в следующий раз
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // файл занят или уже удалён
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
        clips.values().forEach(clip -> clip.subscription.close());
        List.copyOf(channels.values()).forEach(channel -> channel.close(null));
    }
}

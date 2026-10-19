package com.example.dinamika_back.service;

import com.example.dinamika_back.features.ConditionalOnFeature;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.FeatureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Настройки видеоархива из application.properties (ключи video.*) и политика источников из потолка установки
 * ({@code awms.video.allow-file-source}, {@code awms.video.allowed-networks}).
 * Бина нет, когда потолок выключает видео (уровень L3).
 */
@Component
@ConditionalOnFeature(Feature.VIDEO)
public class VideoSettings {

    private final String ffmpegPath;
    private final String ffprobePath;
    private final Path storageRoot;
    private final int maxRecordSeconds;
    private final int connectTimeoutSeconds;
    private final long snapshotCacheMillis;
    private final int liveIdleSeconds;
    private final int liveQueueFrames;
    private final VideoSource.SourcePolicy sourcePolicy;

    @Autowired
    public VideoSettings(
            @Value("${video.ffmpeg-path:C:/ffmpeg/bin/ffmpeg.exe}") String ffmpegPath,
            @Value("${video.ffprobe-path:}") String ffprobePath,
            @Value("${video.storage-dir:video-archive}") String storageDir,
            @Value("${video.max-record-seconds:3600}") int maxRecordSeconds,
            @Value("${video.connect-timeout-seconds:8}") int connectTimeoutSeconds,
            @Value("${video.snapshot-cache-ms:2000}") long snapshotCacheMillis,
            @Value("${video.live-idle-seconds:20}") int liveIdleSeconds,
            @Value("${video.live-queue-frames:150}") int liveQueueFrames,
            FeatureService features) {
        this(ffmpegPath, ffprobePath, storageDir, maxRecordSeconds, connectTimeoutSeconds, snapshotCacheMillis,
                liveIdleSeconds, liveQueueFrames, VideoSource.SourcePolicy.of(features.ceiling()));
    }

    /** Настройки с явной политикой источников — для тестов, создающих классы видео напрямую */
    public VideoSettings(String ffmpegPath, String ffprobePath, String storageDir, int maxRecordSeconds,
                         int connectTimeoutSeconds, long snapshotCacheMillis, int liveIdleSeconds, int liveQueueFrames,
                         VideoSource.SourcePolicy sourcePolicy) {
        this.ffmpegPath = ffmpegPath.trim();
        this.ffprobePath = ffprobePath.isBlank() ? siblingProbe(this.ffmpegPath) : ffprobePath.trim();
        this.storageRoot = Paths.get(storageDir.trim()).toAbsolutePath().normalize();
        this.maxRecordSeconds = Math.max(5, maxRecordSeconds);
        this.connectTimeoutSeconds = Math.max(2, connectTimeoutSeconds);
        this.snapshotCacheMillis = Math.max(0, snapshotCacheMillis);
        this.liveIdleSeconds = Math.max(0, liveIdleSeconds);
        this.liveQueueFrames = Math.max(25, liveQueueFrames);
        this.sourcePolicy = sourcePolicy;
    }

    /** ffprobe лежит рядом с ffmpeg в той же папке bin */
    private static String siblingProbe(String ffmpeg) {
        if (ffmpeg.toLowerCase().endsWith("ffmpeg.exe")) {
            return ffmpeg.substring(0, ffmpeg.length() - "ffmpeg.exe".length()) + "ffprobe.exe";
        }
        if (ffmpeg.endsWith("ffmpeg")) {
            return ffmpeg.substring(0, ffmpeg.length() - "ffmpeg".length()) + "ffprobe";
        }
        return "ffprobe";
    }

    /** Путь к ffmpeg.exe или просто «ffmpeg», если он в PATH */
    public String ffmpegPath() {
        return ffmpegPath;
    }

    public String ffprobePath() {
        return ffprobePath;
    }

    /** Абсолютный путь к каталогу видеоархива */
    public Path storageRoot() {
        return storageRoot;
    }

    /** Предел длительности одной записи: страховка, если событие «окончание» не придёт */
    public int maxRecordSeconds() {
        return maxRecordSeconds;
    }

    /** Сколько ждать ответа камеры при подключении */
    public int connectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    /** Сколько отдавать один и тот же кадр живого просмотра, не дёргая камеру заново */
    public long snapshotCacheMillis() {
        return snapshotCacheMillis;
    }

    /** Сколько держать подключение к камере после ухода последнего зрителя — чтобы переключение окон не переподключало камеру */
    public int liveIdleSeconds() {
        return liveIdleSeconds;
    }

    /** Сколько кадров может отстать зритель живого потока, прежде чем его очередь сбросится до следующего опорного кадра */
    public int liveQueueFrames() {
        return liveQueueFrames;
    }

    /** Каталог записей по кнопке «Записать» — временные файлы до скачивания */
    public Path clipsRoot() {
        return storageRoot.resolve("clips");
    }

    /** Что FFmpeg разрешено открыть: file:/test: и сети камер (потолок установки) */
    public VideoSource.SourcePolicy sourcePolicy() {
        return sourcePolicy;
    }
}

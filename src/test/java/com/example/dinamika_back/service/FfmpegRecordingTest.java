package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.VideoStreamCheckDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Запись через настоящий FFmpeg на тестовом сигнале — тот же путь, что и с камерой:
 * команда записи, остановка «q», проверка длительности, кадр для живого просмотра.
 * Без FFmpeg по пути из VIDEO_FFMPEG (или C:/ffmpeg/bin/ffmpeg.exe) тесты пропускаются.
 */
class FfmpegRecordingTest {

    private static FfmpegRunner ffmpeg;

    @BeforeAll
    static void setUp() {
        String path = System.getenv().getOrDefault("VIDEO_FFMPEG", "C:/ffmpeg/bin/ffmpeg.exe");
        VideoSettings settings = new VideoSettings(path, "", "target/video-test", 3600, 8, 0, 20, 150, VideoTestSupport.OPEN_SOURCES);
        ffmpeg = new FfmpegRunner(settings, new ObjectMapper());
    }

    @Test
    void recordsPlayableMp4AndStopsGracefully(@TempDir Path dir) throws Exception {
        assumeTrue(ffmpeg.version() != null, "FFmpeg не найден — тест пропущен");
        Path target = dir.resolve("2026-10-07").resolve("20261007_120000_authorityEvent_uid_Cam01_test_ST-001.mp4");

        RecorderProcess process = RecorderProcess.start(ffmpeg.recordCommand("test:", target), target);
        Thread.sleep(3_000);
        assertThat(process.isAlive()).as("FFmpeg пишет до команды остановки").isTrue();

        process.stop(15_000);

        assertThat(process.exitCode()).as("остановка «q» — штатное завершение: %s", process.errorText()).isZero();
        assertThat(Files.size(target)).isPositive();
        BigDecimal duration = ffmpeg.durationOf(target);
        assertThat(duration).as("длительность читается ffprobe").isNotNull();
        assertThat(duration.doubleValue()).isBetween(2.0, 5.0);
    }

    @Test
    void snapshotReturnsJpeg() {
        assumeTrue(ffmpeg.version() != null, "FFmpeg не найден — тест пропущен");
        byte[] jpeg = ffmpeg.snapshot("test:");
        assertThat(jpeg.length).isGreaterThan(1000);
        assertThat(jpeg[0] & 0xFF).isEqualTo(0xFF);
        assertThat(jpeg[1] & 0xFF).isEqualTo(0xD8);
    }

    @Test
    void checkReadsStreamParameters() {
        assumeTrue(ffmpeg.version() != null, "FFmpeg не найден — тест пропущен");
        VideoStreamCheckDTO result = ffmpeg.check(1L, "test:");
        assertThat(result.isOk()).as(result.getMessage()).isTrue();
        assertThat(result.getWidth()).isEqualTo(1280);
        assertThat(result.getHeight()).isEqualTo(720);
        assertThat(result.getFps()).isEqualTo(25.0);
    }

    @Test
    void unreachableCameraGivesClearMessage() {
        assumeTrue(ffmpeg.version() != null, "FFmpeg не найден — тест пропущен");
        // 192.0.2.0/24 — адреса для документации, гарантированно недоступны
        VideoStreamCheckDTO result = new FfmpegRunner(
                new VideoSettings(System.getenv().getOrDefault("VIDEO_FFMPEG", "C:/ffmpeg/bin/ffmpeg.exe"),
                        "", "target/video-test", 3600, 2, 0, 20, 150, VideoTestSupport.OPEN_SOURCES),
                new ObjectMapper()).check(1L, "rtsp://admin:x@@192.0.2.10:554/ISAPI/Streaming/Channels/101");
        assertThat(result.isOk()).isFalse();
        assertThat(result.getMessage()).isNotBlank();
    }
}

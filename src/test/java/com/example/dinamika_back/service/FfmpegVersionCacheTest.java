package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.VideoStatusDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Состояние видеомодуля опрашивается страницей каждые 2,5 с: `ffmpeg -version` запускается один раз,
 * а пока FFmpeg не найден — не чаще раза в минуту, чтобы после установки состояние ожило без перезапуска.
 */
class FfmpegVersionCacheTest {

    /** FFmpeg без процессов: версия выдаётся по очереди, время задаёт тест */
    private static final class FakeFfmpeg extends FfmpegRunner {
        private final List<String> versions;
        int runs;
        long clock = 1_000_000;

        FakeFfmpeg(String... versions) {
            super(new VideoSettings("ffmpeg-missing", "", "target/video-test", 3600, 8, 0, 0, 150, VideoTestSupport.OPEN_SOURCES), new ObjectMapper());
            this.versions = Arrays.asList(versions);
        }

        @Override
        public String version() {
            // null — FFmpeg не найден
            String version = runs < versions.size() ? versions.get(runs) : null;
            runs++;
            return version;
        }

        @Override
        long now() {
            return clock;
        }
    }

    @Test
    void foundVersionIsAskedOnce() {
        FakeFfmpeg ffmpeg = new FakeFfmpeg("ffmpeg version 7.1");
        for (int i = 0; i < 100; i++) {
            assertThat(ffmpeg.cachedVersion()).isEqualTo("ffmpeg version 7.1");
            ffmpeg.clock += 2_500;
        }
        assertThat(ffmpeg.runs).isEqualTo(1);
    }

    @Test
    void missingFfmpegIsRecheckedOncePerMinute() {
        FakeFfmpeg ffmpeg = new FakeFfmpeg(null, null, "ffmpeg version 7.1");
        assertThat(ffmpeg.cachedVersion()).isNull();
        // Опрос каждые 2,5 с в течение минуты — повторных запусков нет
        for (int i = 0; i < 23; i++) {
            ffmpeg.clock += 2_500;
            assertThat(ffmpeg.cachedVersion()).isNull();
        }
        assertThat(ffmpeg.runs).isEqualTo(1);

        ffmpeg.clock += FfmpegRunner.VERSION_RETRY_MILLIS;
        assertThat(ffmpeg.cachedVersion()).isNull();
        assertThat(ffmpeg.runs).isEqualTo(2);

        // FFmpeg установили — через минуту состояние оживает и дальше берётся из кэша
        ffmpeg.clock += FfmpegRunner.VERSION_RETRY_MILLIS;
        assertThat(ffmpeg.cachedVersion()).isEqualTo("ffmpeg version 7.1");
        ffmpeg.clock += FfmpegRunner.VERSION_RETRY_MILLIS * 10;
        assertThat(ffmpeg.cachedVersion()).isEqualTo("ffmpeg version 7.1");
        assertThat(ffmpeg.runs).isEqualTo(3);
    }

    @Test
    void realMissingFfmpegGivesNullWithoutError() {
        FfmpegRunner ffmpeg = new FfmpegRunner(
                new VideoSettings("C:/no-such-dir/ffmpeg.exe", "", "target/video-test", 3600, 8, 0, 0, 150, VideoTestSupport.OPEN_SOURCES), new ObjectMapper());
        assertThat(ffmpeg.cachedVersion()).isNull();
        assertThat(ffmpeg.cachedVersion()).isNull();
    }

    @Test
    void statusKeepsFormatAndDoesNotRunFfmpegOnEveryRequest() {
        FakeFfmpeg ffmpeg = new FakeFfmpeg("ffmpeg version 7.1");
        VideoRecordingService recording = mock(VideoRecordingService.class);
        when(recording.activeRecords()).thenReturn(List.of());
        VideoSettings settings = new VideoSettings("ffmpeg", "", "target/video-test", 3600, 8, 0, 0, 150, VideoTestSupport.OPEN_SOURCES);
        VideoArchiveService archive = new VideoArchiveService(null, null, recording, ffmpeg, settings, null,
                VideoTestSupport.FULL);

        // Абсолютный путь архива видят только руководители (раздел 6.5 проекта)
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("director", null,
                List.of(new SimpleGrantedAuthority("DIRECTOR"))));
        VideoStatusDTO status = null;
        try {
            for (int i = 0; i < 10; i++) status = archive.status();
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(ffmpeg.runs).isEqualTo(1);
        assertThat(status.isFfmpegAvailable()).isTrue();
        assertThat(status.getFfmpegVersion()).isEqualTo("ffmpeg version 7.1");
        assertThat(status.getStorageDir()).isEqualTo(settings.storageRoot().toString());
        assertThat(status.getActiveRecords()).isEmpty();

        // Без роли руководителя — без пути, остальное то же
        VideoStatusDTO operator = archive.status();
        assertThat(operator.getStorageDir()).isNull();
        assertThat(operator.isFfmpegAvailable()).isTrue();
        assertThat(ffmpeg.runs).isEqualTo(1);
    }
}

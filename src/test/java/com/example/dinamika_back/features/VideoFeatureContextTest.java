package com.example.dinamika_back.features;

import com.example.dinamika_back.controller.VideoController;
import com.example.dinamika_back.dto.VideoCameraUpdateDTO;
import com.example.dinamika_back.dto.VideoEventRequest;
import com.example.dinamika_back.dto.VideoStatusDTO;
import com.example.dinamika_back.dto.VideoStreamCheckDTO;
import com.example.dinamika_back.model.VideoCamera;
import com.example.dinamika_back.model.VideoRecord;
import com.example.dinamika_back.repository.StationRepository;
import com.example.dinamika_back.repository.VideoCameraRepository;
import com.example.dinamika_back.repository.VideoRecordFileRepository;
import com.example.dinamika_back.repository.VideoRecordRepository;
import com.example.dinamika_back.service.FfmpegRunner;
import com.example.dinamika_back.service.LiveStreamService;
import com.example.dinamika_back.service.VideoArchiveService;
import com.example.dinamika_back.service.VideoCameraService;
import com.example.dinamika_back.service.VideoException;
import com.example.dinamika_back.service.VideoMapper;
import com.example.dinamika_back.service.VideoRecordingService;
import com.example.dinamika_back.service.VideoSettings;
import com.example.dinamika_back.service.VideoSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Видео и выключатели (пакет B2): без {@code video} в потолке нет ни одного из 8 бинов модуля и не выполняется
 * закрытие прерванных записей при старте (L3); выключенная подфункция отвечает 404 сторожем раньше, чем запускается
 * FFmpeg; {@code file:} и {@code test:} по умолчанию отклоняются; адрес камеры проверяется по разрешённым сетям
 * после разрешения имени и до запуска FFmpeg; опрос состояния процессов не запускает; HEAD на поток — 405 без подписки.
 */
class VideoFeatureContextTest {

    /** 8 бинов модуля (таблица 4.2 проекта) */
    private static final List<Class<?>> VIDEO_BEANS = List.of(VideoController.class, VideoSettings.class,
            VideoMapper.class, FfmpegRunner.class, VideoRecordingService.class, LiveStreamService.class,
            VideoCameraService.class, VideoArchiveService.class);

    private final VideoCameraRepository cameraRepository = mock(VideoCameraRepository.class);
    private final VideoRecordRepository recordRepository = mock(VideoRecordRepository.class);
    private final VideoRecordFileRepository fileRepository = mock(VideoRecordFileRepository.class);
    private final StationRepository stationRepository = mock(StationRepository.class);

    /**
     * Контекст модуля видео. Окружение процесса подменяется пустым (как в {@link OnFeatureConditionTest}); FFmpeg —
     * по несуществующему пути: если проверка источника пропустит камеру, запуск закончится «FFmpeg не найден» (503),
     * а не отказом политики, и тест это увидит
     */
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources().replace(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                Map.of())))
                .withPropertyValues("video.ffmpeg-path=C:/no-such-dir/ffmpeg.exe", "video.storage-dir=target/video-feature-test")
                .withBean(VideoCameraRepository.class, () -> cameraRepository)
                .withBean(VideoRecordRepository.class, () -> recordRepository)
                .withBean(VideoRecordFileRepository.class, () -> fileRepository)
                .withBean(StationRepository.class, () -> stationRepository)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withUserConfiguration(FeatureService.class, AccessAudit.Noop.class)
                .withUserConfiguration(VIDEO_BEANS.toArray(Class<?>[]::new));
    }

    private static VideoCamera camera(String url) {
        VideoCamera camera = new VideoCamera();
        camera.setId(1L);
        camera.setName("Cam01");
        camera.setIp("192.168.1.64");
        camera.setCameraType(VideoCamera.TYPE_STATION);
        camera.setRtspUrl(url);
        return camera;
    }

    private static VideoCameraUpdateDTO newUrl(String url) {
        VideoCameraUpdateDTO dto = new VideoCameraUpdateDTO();
        dto.setRtspUrl(url);
        return dto;
    }

    private static HttpStatus statusOf(Throwable e) {
        if (e instanceof VideoException video) return video.status();
        if (e instanceof FeatureDisabledException) return HttpStatus.NOT_FOUND;
        throw new AssertionError("неожиданное исключение", e);
    }

    // ==================== L3: бины модуля ====================

    @Test
    void videoOffCreatesNoModuleBeansAndRunsNothingAtStartup() {
        for (String[] profile : List.<String[]>of(
                new String[]{"awms.features.preset=full", "awms.features.video.enabled=false"},
                new String[]{"awms.features.preset=standard"},
                new String[]{"awms.features.preset=isolated"})) {
            runner().withPropertyValues(profile).run(context -> {
                assertThat(context).hasNotFailed();
                for (Class<?> bean : VIDEO_BEANS) assertThat(context).as(String.join(" ", profile)).doesNotHaveBean(bean);
                assertThat(context).hasSingleBean(FeatureService.class);
            });
        }
        // @PostConstruct recoverInterrupted не выполнялся: к журналу видео никто не обращался
        verifyNoInteractions(recordRepository, fileRepository, cameraRepository, stationRepository);
    }

    @Test
    void fullCreatesModuleAndClosesInterruptedRecords() {
        runner().withPropertyValues("awms.features.preset=full").run(context -> {
            assertThat(context).hasNotFailed();
            for (Class<?> bean : VIDEO_BEANS) assertThat(context).hasSingleBean(bean);
        });
        verify(recordRepository).findByStatus(VideoRecord.STATUS_RECORDING);
    }

    // ==================== Сторож: 404 до запуска FFmpeg ====================

    @Test
    void liveOffAnswers404ForStreamClipAndFrame() {
        runner().withPropertyValues("awms.features.preset=full", "awms.features.video.live.enabled=false").run(context -> {
            LiveStreamService live = context.getBean(LiveStreamService.class);
            VideoCameraService cameras = context.getBean(VideoCameraService.class);
            assertThatThrownBy(() -> live.subscribe(1L)).isInstanceOf(FeatureDisabledException.class);
            // Клипы зависят от живого просмотра — выключены каскадом
            assertThatThrownBy(() -> live.startClip(1L)).isInstanceOf(FeatureDisabledException.class);
            assertThatThrownBy(() -> live.stopClip("x")).isInstanceOf(FeatureDisabledException.class);
            assertThatThrownBy(() -> cameras.snapshot(1L)).isInstanceOf(FeatureDisabledException.class);
            // Проверка камеры — функция video.cameras, она включена
            assertThatThrownBy(() -> cameras.check(99L)).isInstanceOf(VideoException.class);
        });
        verify(cameraRepository, never()).findById(1L);
    }

    @Test
    void clipsOffKeepsLiveView() {
        runner().withPropertyValues("awms.features.preset=full", "awms.features.video.clips.enabled=false").run(context -> {
            LiveStreamService live = context.getBean(LiveStreamService.class);
            assertThatThrownBy(() -> live.startClip(1L)).isInstanceOf(FeatureDisabledException.class);
            // Сторож живого просмотра пропустил: дальше обычная ошибка «камера не найдена» (в БД пусто)
            assertThatThrownBy(() -> live.subscribe(1L))
                    .isInstanceOf(VideoException.class)
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
        });
    }

    @Test
    void recordingAndCamerasOffAnswer404() {
        runner().withPropertyValues("awms.features.preset=full", "awms.features.video.recording.enabled=false",
                "awms.features.video.cameras.enabled=false").run(context -> {
            VideoEventRequest event = new VideoEventRequest();
            event.setStationId(1L);
            event.setEventType("authority");
            event.setPhase("start");
            assertThatThrownBy(() -> context.getBean(VideoRecordingService.class).handleEvent(event))
                    .isInstanceOf(FeatureDisabledException.class);
            VideoCameraService cameras = context.getBean(VideoCameraService.class);
            assertThatThrownBy(() -> cameras.update(1L, newUrl("rtsp://10.0.0.5/s")))
                    .isInstanceOf(FeatureDisabledException.class);
            assertThatThrownBy(() -> cameras.check(1L)).isInstanceOf(FeatureDisabledException.class);
            assertThat(new FeatureDisabledException(Feature.VIDEO_CAMERAS).getStatusCode().value()).isEqualTo(404);
        });
        verifyNoInteractions(stationRepository);
        verify(cameraRepository, never()).findById(1L);
    }

    // ==================== Источники: file:, test:, сети камер ====================

    @Test
    void fileSourceIsRejectedByDefaultOnSaveAndOnLaunch() {
        when(cameraRepository.findById(1L)).thenReturn(Optional.of(camera("rtsp://admin:p@@192.168.1.64:554/s")));
        runner().withPropertyValues("awms.features.preset=full").run(context -> {
            VideoCameraService cameras = context.getBean(VideoCameraService.class);
            for (String url : List.of("file:C:/AWMS/Back/dinamika_back/video-archive/emulator/cam01-station.mp4", "test:")) {
                assertThatThrownBy(() -> cameras.update(1L, newUrl(url)))
                        .as(url)
                        .isInstanceOf(VideoException.class)
                        .hasMessageContaining("awms.video.allow-file-source")
                        .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
            }
        });
        verify(cameraRepository, never()).save(any());

        // Камера, сохранённая с file: раньше, при запуске FFmpeg тоже не проходит — до запуска процесса (403, не 503)
        when(cameraRepository.findById(1L)).thenReturn(Optional.of(camera("file:C:/video/cam01.mp4")));
        runner().withPropertyValues("awms.features.preset=full").run(context -> {
            VideoCameraService cameras = context.getBean(VideoCameraService.class);
            assertThatThrownBy(() -> cameras.snapshot(1L))
                    .isInstanceOf(VideoException.class)
                    .hasMessageContaining("awms.video.allow-file-source")
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.FORBIDDEN));
            VideoStreamCheckDTO check = cameras.check(1L);
            assertThat(check.isOk()).isFalse();
            assertThat(check.getMessage()).contains("awms.video.allow-file-source");
        });
    }

    @Test
    void fileSourceIsAllowedOnlyByExplicitCeilingKey() {
        when(cameraRepository.findById(1L)).thenReturn(Optional.of(camera("rtsp://192.168.1.64/s")));
        when(cameraRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        runner().withPropertyValues("awms.features.preset=full", "awms.video.allow-file-source=true").run(context -> {
            assertThat(context.getBean(VideoSettings.class).sourcePolicy().allowFileSource()).isTrue();
            context.getBean(VideoCameraService.class).update(1L, newUrl("file:C:/video/cam01.mp4"));
        });
        verify(cameraRepository).save(any());
    }

    @Test
    void cameraOutsideAllowedNetworksIsRefusedAfterNameResolutionBeforeFfmpeg() {
        // localhost разрешается в 127.0.0.1 / ::1 — вне частных сетей RFC 1918 (умолчание потолка)
        when(cameraRepository.findById(1L)).thenReturn(Optional.of(camera("rtsp://admin:p@localhost:554/s")));
        runner().withPropertyValues("awms.features.preset=full").run(context -> {
            VideoCameraService cameras = context.getBean(VideoCameraService.class);
            LiveStreamService live = context.getBean(LiveStreamService.class);
            assertThatThrownBy(() -> cameras.snapshot(1L))
                    .isInstanceOf(VideoException.class)
                    .hasMessageContaining("вне разрешённых сетей камер")
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.FORBIDDEN));
            assertThatThrownBy(() -> live.subscribe(1L))
                    .isInstanceOf(VideoException.class)
                    .hasMessageContaining("вне разрешённых сетей камер");
            assertThat(cameras.check(1L).getMessage()).contains("вне разрешённых сетей камер");
            // Сохранить такой адрес тоже нельзя (новый пароль — проверка та же)
            assertThatThrownBy(() -> cameras.update(1L, newUrl("rtsp://admin:new@localhost/s")))
                    .isInstanceOf(VideoException.class)
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        });
        verify(cameraRepository, never()).save(any());

        // Камера в разрешённой сети проходит проверку — дальше запуск FFmpeg (его на этом пути нет → 503)
        when(cameraRepository.findById(1L)).thenReturn(Optional.of(camera("rtsp://admin:p@192.168.1.64:554/s")));
        runner().withPropertyValues("awms.features.preset=full").run(context ->
                assertThatThrownBy(() -> context.getBean(VideoCameraService.class).snapshot(1L))
                        .isInstanceOf(VideoException.class)
                        .hasMessageContaining("Не найден FFmpeg")
                        .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)));
    }

    @Test
    void allowedNetworksComeFromCeiling() {
        runner().withPropertyValues("awms.features.preset=full", "awms.video.allowed-networks=127.0.0.0/8").run(context -> {
            FfmpegRunner ffmpeg = context.getBean(FfmpegRunner.class);
            // Сеть стенда в потолке: localhost разрешён, частные сети — уже нет
            ffmpeg.verifySource("rtsp://admin:p@localhost/s");
            assertThatThrownBy(() -> ffmpeg.verifySource("rtsp://admin:p@192.168.1.64/s"))
                    .isInstanceOf(VideoException.class)
                    .hasMessageContaining("вне разрешённых сетей камер");
        });
        // Сеть с ошибкой записи — модуль видео не запускается, причина называет ключ
        runner().withPropertyValues("awms.features.preset=full", "awms.video.allowed-networks=camera.local").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .isInstanceOf(FeatureConfigurationException.class)
                    .hasMessageContaining("awms.video.allowed-networks");
        });
    }

    // ==================== Состояние модуля без процессов ====================

    /** FFmpeg без процессов: считает, сколько раз определялась версия (единственный путь к `ffmpeg -version`) */
    private static final class CountingFfmpeg extends FfmpegRunner {
        final AtomicInteger versionRuns = new AtomicInteger();

        CountingFfmpeg(VideoSettings settings) {
            super(settings, new ObjectMapper());
        }

        @Override
        public String version() {
            versionRuns.incrementAndGet();
            return "ffmpeg version 9.0";
        }
    }

    private static VideoStatusDTO pollStatus(FeatureService features, CountingFfmpeg ffmpeg, VideoSettings settings) {
        VideoRecordingService recording = mock(VideoRecordingService.class);
        when(recording.activeRecords()).thenReturn(List.of());
        VideoArchiveService archive = new VideoArchiveService(null, null, recording, ffmpeg, settings, null, features);
        VideoStatusDTO status = null;
        for (int i = 0; i < 20; i++) status = archive.status();
        return status;
    }

    @Test
    void statusDoesNotStartFfmpeg() {
        VideoSettings settings = new VideoSettings("ffmpeg", "", "target/video-feature-test", 3600, 8, 0, 0, 150,
                new VideoSource.SourcePolicy(false, List.of()));

        // Включён только журнал: FFmpeg не нужен и не запускается вовсе
        FeatureService archiveOnly = new FeatureService(FeatureResolver.fromProperties(Map.of(
                "awms.features.preset", "full",
                "awms.features.video.live.enabled", "false", "awms.features.video.recording.enabled", "false",
                "awms.features.video.cameras.enabled", "false")));
        assertThat(archiveOnly.isOn(Feature.VIDEO_ARCHIVE)).isTrue();
        assertThat(archiveOnly.isOn(Feature.VIDEO_CLIPS)).isFalse();
        CountingFfmpeg idle = new CountingFfmpeg(settings);
        VideoStatusDTO status = pollStatus(archiveOnly, idle, settings);
        assertThat(idle.versionRuns.get()).isZero();
        assertThat(status.isFfmpegAvailable()).isFalse();
        // Без роли руководителя путь архива не отдаётся
        assertThat(status.getStorageDir()).isNull();

        // Пресет full: версия определяется один раз, дальше — из кэша
        CountingFfmpeg cached = new CountingFfmpeg(settings);
        status = pollStatus(new FeatureService(FeatureResolver.fromProperties(Map.of("awms.features.preset", "full"))),
                cached, settings);
        assertThat(cached.versionRuns.get()).isEqualTo(1);
        assertThat(status.isFfmpegAvailable()).isTrue();
    }

    // ==================== HEAD на потоковые и файловые адреса ====================

    /** Контроллер видео за воротами — как в службе: ворота раньше Spring MVC */
    private static MockMvc gatedController(FeatureService features, VideoCameraService cameras, LiveStreamService live,
                                           VideoArchiveService archive) {
        return MockMvcBuilders.standaloneSetup(new VideoController(cameras, archive, mock(VideoRecordingService.class), live))
                .addFilters(new FeatureGateFilter(features)).build();
    }

    @Test
    void headOnStreamIs405WithoutSubscriptionWhenLiveIsOn() throws Exception {
        // Ворота пропускают (video.live включён), контроллер отвечает 405 до подписки (подробно — VideoControllerTest)
        LiveStreamService live = mock(LiveStreamService.class);
        VideoCameraService cameras = mock(VideoCameraService.class);
        VideoArchiveService archive = mock(VideoArchiveService.class);
        MockMvc mvc = gatedController(new FeatureService(FeatureResolver.fromProperties(Map.of("awms.features.preset", "full"))),
                cameras, live, archive);
        for (String path : List.of("/api/video/cameras/1/live", "/api/video/cameras/1/snapshot",
                "/api/video/files/1/content", "/api/video/files/1/download", "/api/video/clips/abc/download")) {
            mvc.perform(head(path)).andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "GET"));
        }
        verify(live, never()).subscribe(any());
        verifyNoInteractions(cameras, live, archive);
    }

    @Test
    void headAndGetAreClosedByGateWhenSubfeatureIsOff() throws Exception {
        LiveStreamService live = mock(LiveStreamService.class);
        VideoCameraService cameras = mock(VideoCameraService.class);
        VideoArchiveService archive = mock(VideoArchiveService.class);
        FeatureService noLive = new FeatureService(FeatureResolver.fromProperties(Map.of(
                "awms.features.preset", "full", "awms.features.video.live.enabled", "false",
                "awms.features.video.download.enabled", "false")));
        MockMvc mvc = gatedController(noLive, cameras, live, archive);
        for (String path : List.of("/api/video/cameras/1/live", "/api/video/cameras/1/snapshot", "/api/video/live",
                "/api/video/files/1/download", "/api/video/clips/abc/download")) {
            mvc.perform(head(path)).andExpect(status().isNotFound());
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
        verifyNoInteractions(live, cameras, archive);
    }
}

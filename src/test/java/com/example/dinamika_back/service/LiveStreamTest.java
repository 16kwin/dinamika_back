package com.example.dinamika_back.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Живой поток через настоящий FFmpeg на тестовом сигнале 25 к/с: один процесс на камеру, несколько
 * зрителей, у каждого не меньше 24 к/с, новый зритель начинает с опорного кадра.
 * Без FFmpeg по пути из VIDEO_FFMPEG (или C:/ffmpeg/bin/ffmpeg.exe) тесты пропускаются.
 */
class LiveStreamTest {

    private static FfmpegRunner ffmpeg;
    private static String ffmpegPath;

    @BeforeAll
    static void setUp() {
        ffmpegPath = System.getenv().getOrDefault("VIDEO_FFMPEG", "C:/ffmpeg/bin/ffmpeg.exe");
        ffmpeg = new FfmpegRunner(new VideoSettings(ffmpegPath, "", "target/video-test", 3600, 8, 0, 0, 150, VideoTestSupport.OPEN_SOURCES), new ObjectMapper());
    }

    /** Зритель в отдельном потоке: считает фрагменты и проверяет, что первый — опорный кадр */
    private static final class Viewer extends Thread {
        private final LiveSubscription subscription;
        private final int trexFlags;
        private final long millis;
        volatile int frames;
        volatile Boolean firstIsKeyframe;

        Viewer(LiveSubscription subscription, long millis) {
            this.subscription = subscription;
            this.trexFlags = Fmp4.trexDefaultFlags(subscription.init());
            this.millis = millis;
        }

        @Override
        public void run() {
            long deadline = System.currentTimeMillis() + millis;
            try {
                while (System.currentTimeMillis() < deadline) {
                    byte[] fragment = subscription.next(200, TimeUnit.MILLISECONDS);
                    if (fragment == null) continue;
                    if (LiveSubscription.isEnd(fragment)) break;
                    if (firstIsKeyframe == null) firstIsKeyframe = Fmp4.isKeyframe(fragment, trexFlags);
                    frames++;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                subscription.close();
            }
        }
    }

    @Test
    void oneCameraProcessFeedsSeveralViewersAt25Fps() throws Exception {
        assumeTrue(ffmpeg.version() != null, "FFmpeg не найден — тест пропущен");
        LiveStreamService service = new LiveStreamService(null, ffmpeg,
                new VideoSettings(ffmpegPath, "", "target/video-test", 3600, 8, 0, 0, 150, VideoTestSupport.OPEN_SOURCES),
                VideoTestSupport.FULL, VideoTestSupport.NO_AUDIT);
        LiveChannel channel = LiveChannel.start(1L, "test", ffmpeg.liveCommand("test:"), 150, closed -> { });
        try {
            assertThat(channel.awaitReady(15)).as("заголовок потока: %s", channel.failureMessage()).isTrue();
            assertThat(channel.codec()).as("H.264 тестового сигнала").matches("avc1\\.[0-9A-F]{6}");

            // Три зрителя сразу и один позже — всем по 6 секунд
            List<Viewer> viewers = new ArrayList<>();
            for (int i = 0; i < 3; i++) viewers.add(new Viewer(new LiveSubscription(service, channel, channel.subscribe()), 6_000));
            viewers.forEach(Thread::start);
            Thread.sleep(1_300);
            Viewer late = new Viewer(new LiveSubscription(service, channel, channel.subscribe()), 6_000);
            late.start();
            viewers.add(late);
            for (Viewer viewer : viewers) viewer.join(10_000);

            for (Viewer viewer : viewers) {
                // Зритель получает кадры от последнего опорного сразу — их может быть чуть больше 25 × 6
                assertThat(viewer.frames / 6.0).as("кадров в секунду у зрителя").isGreaterThanOrEqualTo(24.0);
                assertThat(viewer.firstIsKeyframe).as("первый кадр зрителя — опорный").isTrue();
            }
            assertThat(channel.fps()).as("частота с камеры за последние секунды").isBetween(23.0, 27.0);
        } finally {
            channel.close(null);
        }
    }

    /** Процесс без вывода — камера, которая подключилась и молчит */
    static final class Silent {
        public static void main(String[] args) throws InterruptedException {
            Thread.sleep(60_000);
        }
    }

    private static List<String> silentProcess() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classes = Path.of(Silent.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        return List.of(java, "-Xmx16m", "-cp", classes, Silent.class.getName());
    }

    @Test
    void viewerResponseEndsAndReleasesSubscriptionWhenCameraSendsNothing() throws Exception {
        // Зритель мог уйти, а писать ему нечего — ошибки записи не будет; ответ заканчивается по простою
        LiveStreamService service = new LiveStreamService(null, ffmpeg,
                new VideoSettings(ffmpegPath, "", "target/video-test", 3600, 8, 0, 0, 150, VideoTestSupport.OPEN_SOURCES),
                VideoTestSupport.FULL, VideoTestSupport.NO_AUDIT);
        LiveChannel channel = LiveChannel.start(2L, "silent", silentProcess(), 150, closed -> { });
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            long started = System.currentTimeMillis();
            try (LiveSubscription subscription = new LiveSubscription(service, channel, channel.subscribe())) {
                assertThat(channel.viewerCount()).isEqualTo(1);
                subscription.streamTo(out, 800);
            }
            long elapsed = System.currentTimeMillis() - started;
            assertThat(elapsed).as("ответ закончился по простою").isBetween(700L, 5_000L);
            assertThat(channel.viewerCount()).as("подписка освобождена").isZero();
            assertThat(channel.isClosed()).as("камера не отключилась сама — закончил именно предел простоя").isFalse();
        } finally {
            channel.close(null);
        }
    }

    @Test
    void viewerGoneOnWriteErrorReleasesSubscription() throws Exception {
        assumeTrue(ffmpeg.version() != null, "FFmpeg не найден — тест пропущен");
        LiveStreamService service = new LiveStreamService(null, ffmpeg,
                new VideoSettings(ffmpegPath, "", "target/video-test", 3600, 8, 0, 0, 150, VideoTestSupport.OPEN_SOURCES),
                VideoTestSupport.FULL, VideoTestSupport.NO_AUDIT);
        LiveChannel channel = LiveChannel.start(3L, "test", ffmpeg.liveCommand("test:"), 150, closed -> { });
        try {
            assertThat(channel.awaitReady(15)).as("заголовок потока: %s", channel.failureMessage()).isTrue();
            // Зритель закрыл окно после нескольких кадров
            OutputStream gone = new OutputStream() {
                int writes;

                @Override
                public void write(int b) throws IOException {
                    write(new byte[]{(byte) b}, 0, 1);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    if (++writes > 5) throw new IOException("Connection reset by peer");
                }
            };
            assertThatThrownBy(() -> {
                try (LiveSubscription subscription = new LiveSubscription(service, channel, channel.subscribe())) {
                    subscription.streamTo(gone, LiveSubscription.STALL_MILLIS);
                }
            }).isInstanceOf(IOException.class);
            assertThat(channel.viewerCount()).isZero();
        } finally {
            channel.close(null);
        }
    }

    @Test
    void hevcCodecStringForCameraStreams(@TempDir Path dir) throws Exception {
        assumeTrue(ffmpeg.version() != null, "FFmpeg не найден — тест пропущен");
        // H.265 как у камер стенда: libx265 есть не во всех сборках FFmpeg — без него тест пропускается
        Path out = dir.resolve("hevc.fmp4");
        FfmpegRunner.ProcessResult result = ffmpeg.run(List.of(ffmpegPath, "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "testsrc2=size=1920x1080:rate=25", "-t", "1", "-c:v", "libx265", "-tag:v", "hev1",
                "-x265-params", "log-level=none", "-f", "mp4", "-movflags", "empty_moov+default_base_moof+frag_every_frame",
                out.toString()), 60_000);
        assumeTrue(result.exitCode() == 0 && Files.exists(out), "libx265 нет в сборке FFmpeg — тест пропущен");

        byte[] data = Files.readAllBytes(out);
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        Fmp4.Box ftyp = Fmp4.readBox(in);
        Fmp4.Box moov = Fmp4.readBox(in);
        assertThat(ftyp.type()).isEqualTo("ftyp");
        assertThat(moov.type()).isEqualTo("moov");
        String codec = Fmp4.codecString(Fmp4.concat(ftyp.bytes(), moov.bytes()));
        // Main-профиль, уровень 4.0/4.1 для 1080p25 — как у камер стенда (hev1.1.6.L120.90)
        assertThat(codec).matches("hev1\\.1\\.6\\.L1[0-9]{2}(\\.[0-9A-F]{2})*");

        Fmp4.Box firstMoof = Fmp4.readBox(in);
        assertThat(firstMoof.type()).isEqualTo("moof");
        assertThat(Fmp4.isKeyframe(firstMoof.bytes(), Fmp4.trexDefaultFlags(Fmp4.concat(ftyp.bytes(), moov.bytes())))).isTrue();
        assertThat(Fmp4.sampleCount(firstMoof.bytes())).isEqualTo(1);
    }
}

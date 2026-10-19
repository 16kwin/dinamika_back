package com.example.dinamika_back.service;

import com.example.dinamika_back.features.FeatureConfigurationException;
import com.example.dinamika_back.features.FeatureResolver;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Разбор строк подключения камер: пароли с «@», уже закодированные пароли, тестовый источник; политика источников
 * установки — file:/test:, разрешённые сети камер, проверка по разрешённому адресу.
 */
class VideoSourceTest {

    @Test
    void encodesAtSignInPassword() {
        // Так ссылки камер стенда записаны в переписке: пароль «fName55@» плюс разделитель «@»
        assertThat(VideoSource.normalizeRtspUrl("rtsp://admin:fName55@@192.168.1.64:554/ISAPI/Streaming/Channels/101"))
                .isEqualTo("rtsp://admin:fName55%40@192.168.1.64:554/ISAPI/Streaming/Channels/101");
        assertThat(VideoSource.normalizeRtspUrl("rtsp://admin:123456q@@192.168.1.70:554/ISAPI/Streaming/Channels/101"))
                .isEqualTo("rtsp://admin:123456q%40@192.168.1.70:554/ISAPI/Streaming/Channels/101");
    }

    @Test
    void keepsAlreadyEncodedPassword() {
        String encoded = "rtsp://admin:fName55%40@192.168.1.64:554/ISAPI/Streaming/Channels/101";
        assertThat(VideoSource.normalizeRtspUrl(encoded)).isEqualTo(encoded);
    }

    @Test
    void keepsPlusAndEncodesOtherSpecials() {
        assertThat(VideoSource.normalizeRtspUrl("rtsp://admin:a+b/c#d@10.0.0.5/stream"))
                .isEqualTo("rtsp://admin:a%2Bb%2Fc%23d@10.0.0.5/stream");
    }

    @Test
    void leavesUrlsWithoutCredentialsUntouched() {
        assertThat(VideoSource.normalizeRtspUrl("rtsp://192.168.1.64:554/ISAPI/Streaming/Channels/101"))
                .isEqualTo("rtsp://192.168.1.64:554/ISAPI/Streaming/Channels/101");
        assertThat(VideoSource.normalizeRtspUrl("rtsp://admin@192.168.1.64:554/ISAPI/Streaming/Channels/101"))
                .isEqualTo("rtsp://admin@192.168.1.64:554/ISAPI/Streaming/Channels/101");
    }

    @Test
    void masksPasswordForLogs() {
        assertThat(VideoSource.maskPassword("rtsp://admin:fName55@@192.168.1.64:554/ISAPI/Streaming/Channels/101"))
                .isEqualTo("rtsp://admin:***@192.168.1.64:554/ISAPI/Streaming/Channels/101");
    }

    /** Входы с умолчаниями потолка: камеры в частных сетях, file:/test: запрещены; IP-адреса без DNS */
    private static List<String> input(String url, boolean realtime) {
        return VideoSource.inputArgs(url, 8, realtime, VideoTestSupport.DEFAULT_SOURCES, InetAddress::getAllByName);
    }

    /** Входы как на стенде: file:/test: разрешены */
    private static List<String> standInput(String url, boolean realtime) {
        return VideoSource.inputArgs(url, 8, realtime, VideoTestSupport.OPEN_SOURCES, InetAddress::getAllByName);
    }

    /** DNS для тестов: имя → заданные адреса, IP-адрес — он сам, остальные имена не разрешаются */
    private static VideoSource.HostResolver dns(Map<String, List<String>> names) {
        return host -> {
            if (host.matches("[0-9.]+") || host.contains(":")) return InetAddress.getAllByName(host);
            List<String> addresses = names.get(host);
            if (addresses == null) throw new UnknownHostException(host);
            InetAddress[] result = new InetAddress[addresses.size()];
            for (int i = 0; i < result.length; i++) result[i] = InetAddress.getByName(addresses.get(i));
            return result;
        };
    }

    private static VideoException videoError(Throwable e) {
        return (VideoException) e;
    }

    @Test
    void rtspInputUsesTcpAndTimeout() {
        List<String> args = input("rtsp://admin:p@@10.0.0.5/s", true);
        assertThat(args).containsExactly("-protocol_whitelist", "rtsp,rtp,tcp,udp", "-rtsp_transport", "tcp",
                "-timeout", "8000000", "-i", "rtsp://admin:p%40@10.0.0.5/s");
    }

    @Test
    void transportParamSwitchesToUdpAndIsNotSentToCamera() {
        // Станционная камера стенда обрывает TCP-сессию через 1–3 секунды, по UDP пишет без потерь
        List<String> args = input(
                "rtsp://admin:fName55@@192.168.1.64:554/ISAPI/Streaming/Channels/101?transport=udp", true);
        assertThat(args).containsExactly("-protocol_whitelist", "rtsp,rtp,tcp,udp", "-rtsp_transport", "udp",
                "-timeout", "8000000", "-i", "rtsp://admin:fName55%40@192.168.1.64:554/ISAPI/Streaming/Channels/101");
    }

    // ==================== Политика источников (потолок установки) ====================

    @Test
    void fileAndTestSourcesAreRejectedByDefault() {
        // awms.video.allow-file-source=false по умолчанию: FFmpeg не открывает файлы сервера и не рисует «камеру»
        for (String url : List.of("file:C:/AWMS/video-archive/emulator/cam01.mp4", "test:", " FILE:/etc/passwd")) {
            assertThatThrownBy(() -> input(url, true))
                    .as(url)
                    .isInstanceOf(VideoException.class)
                    .hasMessageContaining("awms.video.allow-file-source")
                    .satisfies(e -> assertThat(videoError(e).status()).isEqualTo(HttpStatus.FORBIDDEN));
        }
        assertThat(VideoTestSupport.DEFAULT_SOURCES.allowFileSource()).isFalse();
    }

    @Test
    void onlyRtspFileAndTestReachFfmpeg() {
        // Строку другого вида FFmpeg понял бы как протокол или путь: http:, concat:, файл сервера
        for (String url : List.of("http://10.0.0.5/s", "concat:a.mp4|b.mp4", "C:/Windows/win.ini", "rtmp://10.0.0.5/s",
                "rtsps://10.0.0.5/s", "")) {
            assertThatThrownBy(() -> standInput(url, false))
                    .as(url)
                    .isInstanceOf(VideoException.class)
                    .satisfies(e -> assertThat(videoError(e).status()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    @Test
    void defaultNetworksArePrivateRfc1918() throws Exception {
        VideoSource.SourcePolicy policy = VideoTestSupport.DEFAULT_SOURCES;
        for (String ip : List.of("10.0.0.1", "10.255.255.255", "172.16.0.1", "172.31.255.254", "192.168.1.64")) {
            assertThat(policy.allows(InetAddress.getByName(ip))).as(ip).isTrue();
        }
        for (String ip : List.of("172.15.255.255", "172.32.0.1", "192.169.0.1", "8.8.8.8", "127.0.0.1", "0.0.0.0",
                "192.0.2.10", "169.254.1.1", "::1", "fd00::1")) {
            assertThat(policy.allows(InetAddress.getByName(ip))).as(ip).isFalse();
        }
    }

    @Test
    void cameraOutsideAllowedNetworksIsRefusedBeforeFfmpeg() {
        for (String url : List.of("rtsp://admin:p@8.8.8.8/s", "rtsp://127.0.0.1:554/s", "rtsp://[::1]/s",
                "rtsp://admin:p@192.0.2.10:554/ISAPI/Streaming/Channels/101")) {
            assertThatThrownBy(() -> input(url, true))
                    .as(url)
                    .isInstanceOf(VideoException.class)
                    .hasMessageContaining("вне разрешённых сетей камер")
                    .hasMessageNotContaining("p@")
                    .satisfies(e -> assertThat(videoError(e).status()).isEqualTo(HttpStatus.FORBIDDEN));
        }
    }

    @Test
    void cameraNameIsResolvedOnceAndFfmpegGetsTheCheckedAddress() {
        // Защита от подмены DNS: FFmpeg получает проверенный IP, а не имя, и сам его уже не разрешает
        VideoSource.HostResolver resolver = dns(Map.of(
                "cam01.local", List.of("192.168.1.64"),
                "evil.example", List.of("8.8.8.8"),
                "mixed.example", List.of("8.8.4.4", "10.1.2.3")));
        assertThat(VideoSource.inputArgs("rtsp://admin:fName55@@cam01.local:554/ISAPI/Streaming/Channels/101?transport=udp",
                8, true, VideoTestSupport.DEFAULT_SOURCES, resolver))
                .containsExactly("-protocol_whitelist", "rtsp,rtp,tcp,udp", "-rtsp_transport", "udp", "-timeout", "8000000",
                        "-i", "rtsp://admin:fName55%40@192.168.1.64:554/ISAPI/Streaming/Channels/101");
        // Из нескольких адресов берётся разрешённый
        assertThat(VideoSource.checkedSource("rtsp://mixed.example/s", VideoTestSupport.DEFAULT_SOURCES, resolver))
                .isEqualTo("rtsp://10.1.2.3/s");
        assertThatThrownBy(() -> VideoSource.checkedSource("rtsp://admin:p@evil.example/s",
                VideoTestSupport.DEFAULT_SOURCES, resolver))
                .isInstanceOf(VideoException.class)
                .hasMessageContaining("evil.example (8.8.8.8)")
                .satisfies(e -> assertThat(videoError(e).status()).isEqualTo(HttpStatus.FORBIDDEN));
        // Имя не разрешилось — камера недоступна (503), FFmpeg не запускается
        assertThatThrownBy(() -> VideoSource.checkedSource("rtsp://unknown.local/s", VideoTestSupport.DEFAULT_SOURCES,
                resolver))
                .isInstanceOf(VideoException.class)
                .satisfies(e -> assertThat(videoError(e).status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void checkedHostIsTheHostFfmpegConnectsTo() {
        // Логин и пароль с «@», «/», «?» кодируются — адрес камеры для проверки и для FFmpeg один и тот же
        VideoSource.HostResolver resolver = dns(Map.of("evil.example", List.of("8.8.8.8")));
        assertThat(VideoSource.checkedSource("rtsp://evil.example/x@10.0.0.5/s", VideoTestSupport.DEFAULT_SOURCES, resolver))
                .isEqualTo("rtsp://evil.example%2Fx@10.0.0.5/s");
        assertThat(VideoSource.checkedSource("rtsp://admin:a?b#c@10.0.0.5", VideoTestSupport.DEFAULT_SOURCES, resolver))
                .isEqualTo("rtsp://admin:a%3Fb%23c@10.0.0.5");
        assertThatThrownBy(() -> VideoSource.checkedSource("rtsp://10.0.0.5@evil.example/s",
                VideoTestSupport.DEFAULT_SOURCES, resolver))
                .isInstanceOf(VideoException.class)
                .hasMessageContaining("вне разрешённых сетей камер");
        // Порт не числом и пустой адрес — неверная ссылка
        for (String url : List.of("rtsp://10.0.0.5:55x4/s", "rtsp://admin:p@/s", "rtsp://[fd00::1/s")) {
            assertThatThrownBy(() -> VideoSource.checkedSource(url, VideoTestSupport.DEFAULT_SOURCES, resolver))
                    .as(url)
                    .isInstanceOf(VideoException.class)
                    .satisfies(e -> assertThat(videoError(e).status()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    @Test
    void ipv6CameraNetworkAndAddress() {
        VideoSource.SourcePolicy policy = new VideoSource.SourcePolicy(false, List.of(VideoSource.Network.parse("fd00::/8")));
        assertThat(VideoSource.checkedSource("rtsp://admin:p@[FD00::5]:554/s", policy, InetAddress::getAllByName))
                .isEqualTo("rtsp://admin:p@[fd00:0:0:0:0:0:0:5]:554/s");
        assertThatThrownBy(() -> VideoSource.checkedSource("rtsp://[fe80::1]/s", policy, InetAddress::getAllByName))
                .isInstanceOf(VideoException.class);
        // IPv4 не попадает в сеть IPv6 и наоборот
        assertThatThrownBy(() -> VideoSource.checkedSource("rtsp://10.0.0.5/s", policy, InetAddress::getAllByName))
                .isInstanceOf(VideoException.class);
    }

    @Test
    void networksParseWithoutDns() throws Exception {
        assertThat(VideoSource.Network.parse("192.168.1.64").contains(InetAddress.getByName("192.168.1.64"))).isTrue();
        assertThat(VideoSource.Network.parse("192.168.1.64").contains(InetAddress.getByName("192.168.1.65"))).isFalse();
        assertThat(VideoSource.Network.parse(" 10.20.0.0/14 ").contains(InetAddress.getByName("10.23.255.1"))).isTrue();
        assertThat(VideoSource.Network.parse("10.20.0.0/14").contains(InetAddress.getByName("10.24.0.1"))).isFalse();
        assertThat(VideoSource.Network.parse("0.0.0.0/0").contains(InetAddress.getByName("8.8.8.8"))).isTrue();
        assertThat(VideoSource.Network.parse("fd00::/8").contains(InetAddress.getByName("fdff::1"))).isTrue();
        // Имя узла, ошибки записи — не сеть (список сетей не зависит от DNS)
        for (String bad : List.of("camera.local", "localhost", "10.0.0", "300.1.1.1", "10.0.0.0/33", "10.0.0.0/",
                "10.0.0.0/x", "fd00::/129", "fd00::1%eth0", "", "::g")) {
            assertThat(VideoSource.Network.parse(bad)).as(bad).isNull();
        }
    }

    @Test
    void ceilingNetworksAreValidatedAtStartup() {
        // Сеть с ошибкой в потолке отменяет запуск модуля видео, сообщение называет ключ
        assertThatThrownBy(() -> VideoSource.SourcePolicy.of(FeatureResolver.fromProperties(Map.of(
                "awms.features.preset", "full", "awms.video.allowed-networks", "192.168.0.0/16,camera.local"))))
                .isInstanceOf(FeatureConfigurationException.class)
                .hasMessageContaining("awms.video.allowed-networks")
                .hasMessageContaining("camera.local");
        VideoSource.SourcePolicy stand = VideoSource.SourcePolicy.of(FeatureResolver.fromProperties(Map.of(
                "awms.features.preset", "full", "awms.video.allow-file-source", "true",
                "awms.video.allowed-networks", "192.168.1.0/24")));
        assertThat(stand.allowFileSource()).isTrue();
        assertThat(stand.networks()).hasSize(1);
    }

    @Test
    void transportParamKeepsOtherParamsAndDefaultsToTcp() {
        assertThat(VideoSource.streamUrl("rtsp://admin:p@@10.0.0.5/s?transportmode=unicast&transport=UDP"))
                .isEqualTo("rtsp://admin:p%40@10.0.0.5/s?transportmode=unicast");
        assertThat(VideoSource.transport("rtsp://admin:p@@10.0.0.5/s?transport=UDP")).isEqualTo("udp");
        assertThat(VideoSource.transport("rtsp://admin:p@@10.0.0.5/s?transport=tcp")).isEqualTo("tcp");
        assertThat(VideoSource.transport("rtsp://admin:p@@10.0.0.5/s")).isEqualTo("tcp");
        // «?» в пароле — не начало параметров
        assertThat(VideoSource.transport("rtsp://admin:a?transport=udp@10.0.0.5/s")).isEqualTo("tcp");
    }

    @Test
    void ffmpegErrorsDoNotLeakPasswords() {
        assertThat(FfmpegRunner.explain("Error opening input rtsp://admin:fName55%40@192.168.1.64:554/x: Invalid data"))
                .doesNotContain("fName55")
                .contains("rtsp://admin:***@192.168.1.64");
        assertThat(FfmpegRunner.explain("[in#0/rtsp @ 0] Error during demuxing: Error number -10054 occurred"))
                .contains("?transport=udp");
    }

    @Test
    void pageGetsMaskedUrlAndUnchangedMaskKeepsStoredPassword() {
        String stored = "rtsp://admin:fName55@@192.168.1.64:554/ISAPI/Streaming/Channels/101?transport=udp";
        String masked = VideoSource.maskPassword(stored);
        assertThat(masked).isEqualTo("rtsp://admin:***@192.168.1.64:554/ISAPI/Streaming/Channels/101?transport=udp");

        // Страница прислала ссылку как есть — пароль берётся сохранённый
        assertThat(VideoSource.restorePassword(masked, stored)).isEqualTo(stored);
        // Тот же сервер и логин, другой путь и параметры — пароль подставляется
        assertThat(VideoSource.restorePassword("rtsp://admin:***@192.168.1.64:554/ISAPI/Streaming/Channels/102", stored))
                .isEqualTo("rtsp://admin:fName55@@192.168.1.64:554/ISAPI/Streaming/Channels/102");
        // Ввели новый пароль — он и сохраняется
        assertThat(VideoSource.restorePassword("rtsp://admin:new@@192.168.1.64:554/s", stored))
                .isEqualTo("rtsp://admin:new@@192.168.1.64:554/s");
        assertThat(VideoSource.restorePassword("test:", stored)).isEqualTo("test:");
    }

    @Test
    void maskedPasswordIsNotRestoredForAnotherServerOrUser() {
        String stored = "rtsp://admin:fName55@@192.168.1.64:554/ISAPI/Streaming/Channels/101";
        // Чужой хост, порт, логин; порт не числом; адрес, похожий на сохранённый
        for (String url : List.of(
                "rtsp://admin:***@10.0.0.99:554/ISAPI/Streaming/Channels/101",
                "rtsp://admin:***@evil.example/ISAPI/Streaming/Channels/101",
                "rtsp://admin:***@192.168.1.64:8554/ISAPI/Streaming/Channels/101",
                "rtsp://operator:***@192.168.1.64:554/ISAPI/Streaming/Channels/101",
                "rtsp://evil.example@admin:***@192.168.1.64:554/s",
                "rtsp://admin:***@192.168.1.64.evil.example:554/s",
                "rtsp://admin:***@192.168.1.64:554x/s",
                "rtsp://admin:***@192.168.1.64:5540/s?transport=udp")) {
            assertThatThrownBy(() -> VideoSource.restorePassword(url, stored))
                    .as(url)
                    .isInstanceOf(VideoException.class)
                    .hasMessage("Адрес камеры изменился — введите пароль заново")
                    .satisfies(e -> assertThat(((VideoException) e).status()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        // Сохранённого пароля нет — подставлять нечего
        assertThatThrownBy(() -> VideoSource.restorePassword("rtsp://admin:***@192.168.1.64/s", "test:"))
                .isInstanceOf(VideoException.class);
        assertThatThrownBy(() -> VideoSource.restorePassword("rtsp://admin:***@192.168.1.64/s", null))
                .isInstanceOf(VideoException.class);
        assertThatThrownBy(() -> VideoSource.restorePassword("rtsp://admin:***@192.168.1.64/s", "rtsp://192.168.1.64/s"))
                .isInstanceOf(VideoException.class);
    }

    @Test
    void maskedPasswordStaysWithinStoredServerEvenWithTrickyUrls() {
        String stored = "rtsp://admin:fName55@@192.168.1.64:554/s";
        // «***» не отделяется как пароль — ссылка остаётся как есть, сохранённый пароль в неё не попадает
        for (String url : List.of(
                "rtsp://admin:***@evil.example@192.168.1.64:554/s",
                "rtsp://admin:***@192.168.1.64:554@evil.example/s",
                "rtsp://admin:***@evil.example/s?x=@192.168.1.64:554")) {
            assertThat(VideoSource.restorePassword(url, stored)).as(url).isEqualTo(url).doesNotContain("fName55");
        }
    }

    @Test
    void maskedPasswordRestoredForSameServerWrittenDifferently() {
        // Регистр хоста и схемы, порт по умолчанию 554, параметр transport
        assertThat(VideoSource.restorePassword("rtsp://admin:***@Camera-01.local/s?transport=udp",
                "RTSP://admin:p@ss@camera-01.LOCAL:554/s"))
                .isEqualTo("rtsp://admin:p@ss@Camera-01.local/s?transport=udp");
        assertThat(VideoSource.restorePassword("rtsp://admin:***@10.0.0.5:554", "rtsp://admin:p@ss@10.0.0.5"))
                .isEqualTo("rtsp://admin:p@ss@10.0.0.5:554");
        // Логин, уже закодированный страницей
        assertThat(VideoSource.restorePassword("rtsp://user%40corp:***@10.0.0.5/s", "rtsp://user@corp:p@10.0.0.5/s"))
                .isEqualTo("rtsp://user%40corp:p@10.0.0.5/s");
        // IPv6
        assertThat(VideoSource.restorePassword("rtsp://admin:***@[FE80::1]/s", "rtsp://admin:p@[fe80::1]:554/s"))
                .isEqualTo("rtsp://admin:p@[FE80::1]/s");
        assertThatThrownBy(() -> VideoSource.restorePassword("rtsp://admin:***@[fe80::2]/s", "rtsp://admin:p@[fe80::1]/s"))
                .isInstanceOf(VideoException.class);
        // Маска, которую страница получает для ссылки без пути, возвращается к тому же паролю
        String stored = "rtsp://admin:fName55@@192.168.1.64:554";
        assertThat(VideoSource.restorePassword(VideoSource.maskPassword(stored), stored)).isEqualTo(stored);
    }

    @Test
    void maskHidesPasswordInUrlWithoutPath() {
        // Ссылка без пути раньше уходила на страницу и в журнал с паролем
        assertThat(VideoSource.maskPassword("rtsp://admin:fName55@@192.168.1.64:554"))
                .isEqualTo("rtsp://admin:***@192.168.1.64:554");
        assertThat(VideoSource.maskPassword("rtsp://admin:secret@192.168.1.64:554?transport=udp"))
                .isEqualTo("rtsp://admin:***@192.168.1.64:554?transport=udp");
        assertThat(VideoSource.maskPassword("rtsp://admin:secret@192.168.1.64"))
                .isEqualTo("rtsp://admin:***@192.168.1.64");
        assertThat(VideoSource.maskPassword("rtsp://192.168.1.64:554")).isEqualTo("rtsp://192.168.1.64:554");
    }

    @Test
    void ffmpegErrorsHidePasswordWithEmptyUserAndUpperCaseScheme() {
        assertThat(FfmpegRunner.explain("Error opening input RTSP://admin:topsecret@10.0.0.5/x: Invalid data"))
                .doesNotContain("topsecret").contains("RTSP://admin:***@10.0.0.5");
        assertThat(FfmpegRunner.explain("Error opening input rtsp://:topsecret@10.0.0.5/x: Invalid data"))
                .doesNotContain("topsecret").contains("rtsp://:***@10.0.0.5");
    }

    @Test
    void videoRolesAcceptBothNamingStyles() {
        assertThat(VideoAccess.roleName("ADMIN")).isEqualTo("ADMIN");
        assertThat(VideoAccess.roleName("ROLE_DIRECTOR")).isEqualTo("DIRECTOR");
        assertThat(VideoAccess.MANAGER_ROLES).contains("ADMIN", "DIRECTOR").doesNotContain("OPERATOR");
    }

    @Test
    void fileEmulatorLoopsOnlyForRecordingAndLive() {
        String url = "file:C:/AWMS/video-archive/emulator/cam01.mp4";
        // Эмулятор — только локальный файл: путь вида http://… или concat:… FFmpeg не откроет
        assertThat(standInput(url, true)).containsExactly("-re", "-stream_loop", "-1",
                "-protocol_whitelist", "file", "-i", "C:/AWMS/video-archive/emulator/cam01.mp4");
        // ffprobe для проверки потока не знает -stream_loop
        assertThat(standInput(url, false)).containsExactly("-protocol_whitelist", "file",
                "-i", "C:/AWMS/video-archive/emulator/cam01.mp4");
        assertThat(VideoSource.needsEncoding(url)).isFalse();
    }

    @Test
    void testSourceUsesLavfiAndRealtimeOnlyForRecording() {
        assertThat(standInput("test:", true)).startsWith("-re", "-f", "lavfi");
        assertThat(standInput("test:", false)).startsWith("-f", "lavfi");
        assertThat(VideoSource.needsEncoding("test:")).isTrue();
        assertThat(VideoSource.needsEncoding("rtsp://10.0.0.5/s")).isFalse();
    }

    @Test
    void fileNamePartsAreSafeForWindows() {
        assertThat(VideoRecordingService.safe("Cam 01")).isEqualTo("Cam-01");
        assertThat(VideoRecordingService.safe("a:b/c")).isEqualTo("a-b-c");
        assertThat(VideoRecordingService.safe("192.168.1.64")).isEqualTo("192.168.1.64");
        assertThat(VideoRecordingService.safe(null)).isEqualTo("none");
    }
}

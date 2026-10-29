package com.example.dinamika_back.features;

import com.example.dinamika_back.features.net.IpNetwork;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Параметры потолка анонимного доступа (пакет B5): {@code awms.security.anonymous.allowed-networks} (пусто —
 * откуда угодно, как раньше) и {@code awms.security.trusted-proxies} (умолчание 127.0.0.1 и ::1). Оба — в белом
 * списке потолка и в отпечатке; запись с ошибкой отменяет запуск и называет ключ; из окружения — предупреждение,
 * а не отказ (как у других параметров безопасности). Сети — только цифрами, без DNS.
 */
class SecurityNetworksParamTest {

    private static FeatureSnapshot ceiling(String... keyValues) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("awms.features.preset", "full");
        for (int i = 0; i < keyValues.length; i += 2) properties.put(keyValues[i], keyValues[i + 1]);
        return FeatureResolver.fromProperties(properties);
    }

    @Test
    void defaultsKeepServerBehaviour() {
        FeatureSnapshot s = ceiling();
        assertThat(s.listParam(CeilingParam.ANONYMOUS_ALLOWED_NETWORKS)).isEmpty();
        assertThat(s.listParam(CeilingParam.TRUSTED_PROXIES)).containsExactly("127.0.0.1", "::1");
        assertThat(s.paramOrigin(CeilingParam.TRUSTED_PROXIES)).isEqualTo("умолчание");
    }

    @Test
    void networksAreCanonicalAndInFingerprint() {
        FeatureSnapshot a = ceiling("awms.security.anonymous.allowed-networks", " 10.0.6.17 ,10.0.5.0/24,,");
        FeatureSnapshot b = ceiling("awms.security.anonymous.allowed-networks", "10.0.5.0/24,10.0.6.17");
        assertThat(a.listParam(CeilingParam.ANONYMOUS_ALLOWED_NETWORKS)).containsExactly("10.0.5.0/24", "10.0.6.17");
        assertThat(a.fingerprint()).isEqualTo(b.fingerprint());
        assertThat(a.fingerprint()).isNotEqualTo(ceiling().fingerprint());
        assertThat(ceiling("awms.security.trusted-proxies", "127.0.0.1").fingerprint()).isNotEqualTo(ceiling().fingerprint());
        // Пустая строка — без ограничений, как без строки
        assertThat(ceiling("awms.security.anonymous.allowed-networks", "").fingerprint()).isEqualTo(ceiling().fingerprint());
    }

    @Test
    void badEntryCancelsStartAndNamesTheKey() {
        for (String bad : List.of("station-1.zavod.local", "10.0.5.0/33", "10.0.5.256", "10.0.5.0/", "*", "10.0.5.0/24 x",
                "fd00::/129", "0.0.0.0/-1")) {
            assertThatThrownBy(() -> ceiling("awms.security.anonymous.allowed-networks", "10.0.6.17," + bad))
                    .isInstanceOf(FeatureConfigurationException.class)
                    .hasMessageContaining("awms.security.anonymous.allowed-networks")
                    .hasMessageContaining("имена узлов не допускаются");
        }
        assertThatThrownBy(() -> ceiling("awms.security.trusted-proxies", "nginx"))
                .isInstanceOf(FeatureConfigurationException.class)
                .hasMessageContaining("awms.security.trusted-proxies");
    }

    @Test
    void ceilingFileAcceptsTheKeys(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("awms-features.properties");
        Files.writeString(file, "awms.features.preset=standard\n"
                + "awms.security.anonymous.allowed-networks=10.0.5.0/24,10.0.6.17\n"
                + "awms.security.trusted-proxies=127.0.0.1\n", StandardCharsets.ISO_8859_1);
        FeatureSnapshot s = FeatureResolver.resolveFile(file);
        assertThat(s.listParam(CeilingParam.ANONYMOUS_ALLOWED_NETWORKS)).containsExactly("10.0.5.0/24", "10.0.6.17");
        assertThat(s.listParam(CeilingParam.TRUSTED_PROXIES)).containsExactly("127.0.0.1");
        assertThat(s.paramOrigin(CeilingParam.ANONYMOUS_ALLOWED_NETWORKS)).contains("awms-features.properties");
    }

    @Test
    void environmentOverrideIsOnlyAWarning(@TempDir Path dir) {
        // Как у server.port и других параметров безопасности: служба запускается, отчёт и лог предупреждают
        FeatureSnapshot s = FeatureResolver.resolve(FeatureResolverTest.environment(
                Map.of("AWMS_SECURITY_ANONYMOUS_ALLOWEDNETWORKS", "10.0.5.0/24"), dir.resolve("missing.properties")),
                dir.resolve("missing.properties"), FeatureResolver.class.getClassLoader());
        assertThat(s.listParam(CeilingParam.ANONYMOUS_ALLOWED_NETWORKS)).containsExactly("10.0.5.0/24");
        assertThat(s.warnings()).anySatisfy(w -> assertThat(w).contains("awms.security.anonymous.allowed-networks")
                .contains("вне файла потолка"));
    }

    /**
     * Шаблоны поставки: строки сетей закомментированы (по умолчанию — как сейчас), а раскомментированный пример
     * проходит проверку службы. Справочник ключей называет оба параметра
     */
    @Test
    void deployTemplatesCarryWorkingCommentedExamples(@TempDir Path dir) throws IOException {
        Path root = Path.of("").toAbsolutePath();
        for (String name : List.of("deploy/features/isolated.properties", "deploy/features/standard.properties",
                "deploy/features/full.properties", "config/awms-features.properties.example")) {
            Path template = root.resolve(name);
            FeatureSnapshot as = FeatureResolver.resolveFile(template);
            assertThat(as.listParam(CeilingParam.ANONYMOUS_ALLOWED_NETWORKS)).as(name).isEmpty();
            assertThat(as.listParam(CeilingParam.TRUSTED_PROXIES)).as(name).containsExactly("127.0.0.1", "::1");

            String text = Files.readString(template, StandardCharsets.UTF_8);
            assertThat(text).as(name).contains("#awms.security.anonymous.allowed-networks=")
                    .contains("#awms.security.trusted-proxies=");
            Path uncommented = dir.resolve(template.getFileName().toString());
            Files.writeString(uncommented, text.replaceAll("(?m)^#(awms\\.security\\.)", "$1"), StandardCharsets.UTF_8);
            FeatureSnapshot example = FeatureResolver.resolveFile(uncommented);
            assertThat(example.listParam(CeilingParam.ANONYMOUS_ALLOWED_NETWORKS)).as(name)
                    .containsExactly("10.0.5.0/24", "10.0.6.17");
        }
    }

    @Test
    void ipNetworkParsesDigitsOnly() throws Exception {
        assertThat(IpNetwork.parse("10.0.5.0/24").contains(IpNetwork.literal("10.0.5.255"))).isTrue();
        assertThat(IpNetwork.parse("10.0.5.0/24").contains(IpNetwork.literal("10.0.6.0"))).isFalse();
        assertThat(IpNetwork.parse("10.0.6.17").contains(IpNetwork.literal("10.0.6.17"))).isTrue();
        assertThat(IpNetwork.parse("10.0.6.17").contains(IpNetwork.literal("10.0.6.18"))).isFalse();
        assertThat(IpNetwork.parse("0.0.0.0/0").contains(IpNetwork.literal("203.0.113.9"))).isTrue();
        assertThat(IpNetwork.parse("10.0.0.0/9").contains(IpNetwork.literal("10.127.255.255"))).isTrue();
        assertThat(IpNetwork.parse("10.0.0.0/9").contains(IpNetwork.literal("10.128.0.0"))).isFalse();
        // IPv6, IPv4 в форме IPv6, зона и скобки
        assertThat(IpNetwork.parse("fd00::/8").contains(IpNetwork.literal("fd12:3456::1"))).isTrue();
        assertThat(IpNetwork.parse("::1").contains(IpNetwork.literal("0:0:0:0:0:0:0:1"))).isTrue();
        assertThat(IpNetwork.parse("10.0.5.0/24").contains(IpNetwork.literal("::ffff:10.0.5.7"))).isTrue();
        assertThat(IpNetwork.parse("::ffff:10.0.5.0/120").contains(IpNetwork.literal("10.0.5.7"))).isTrue();
        assertThat(IpNetwork.literal("[fe80::1%eth0]")).isEqualTo(InetAddress.getByName("fe80::1").getAddress());
        // IPv4 и IPv6 между собой не совпадают
        assertThat(IpNetwork.parse("0.0.0.0/0").contains(IpNetwork.literal("fd00::1"))).isFalse();
        // Имена узлов не разрешаются через DNS
        assertThat(IpNetwork.literal("localhost")).isNull();
        assertThat(IpNetwork.literal("cafe")).isNull();
        assertThat(IpNetwork.parse("station-1")).isNull();
        // Строки, которые InetAddress отправил бы в DNS или разобрал бы с цифрами не ASCII
        for (String odd : List.of(".:", ".1:2", "٣::1", "::٣", "g::1", "1::2::3")) {
            assertThat(IpNetwork.literal(odd)).as(odd).isNull();
        }
    }
}

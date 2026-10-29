package com.example.dinamika_back.features;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.env.SystemEnvironmentPropertySourceEnvironmentPostProcessor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Разбор потолка (раздел 5.2 проекта) на окружении, собранном как у службы: classpath application.properties
 * импортирует файл потолка по AWMS_FEATURES_FILE, окружение процесса и системные свойства задаёт тест.
 */
@ExtendWith(OutputCaptureExtension.class)
class FeatureResolverTest {

    @TempDir
    Path dir;

    // ---------- Пресеты, переопределения, каскад ----------

    @Test
    void withoutCeilingFileFullPresetAndWarning() {
        // Без файла потолка сервер работает как до выключателей — пресет full; сузить набор можно только файлом
        StandardEnvironment env = environment(Map.of(), dir.resolve("missing.properties"));
        FeatureSnapshot s = resolve(env);
        assertThat(s.preset()).isEqualTo(FeaturePreset.FULL);
        assertThat(s.presetSource()).isEqualTo("default");
        assertThat(s.ceilingFileExists()).isFalse();
        assertThat(s.warnings()).anySatisfy(w -> assertThat(w).contains("Файл потолка не найден").contains("full"));
        assertThat(Feature.values()).allSatisfy(f ->
                assertThat(s.isOn(f)).as(f.key()).isEqualTo(f.isOnIn(FeaturePreset.FULL)));
        // Модули и ядро открыты; служебное (testsync) выключено и в full — его ворота закрыты
        assertThat(s.closedRuleFor("GET", "/api/video/status", null)).isNull();
        assertThat(s.closedRuleFor("GET", "/api/nomenclature/tree", null)).isNull();
        assertThat(s.closedRuleFor("POST", "/api/sync/receive", null)).isNotNull();
    }

    @Test
    void ceilingFileWithoutPresetKeyMeansFull() throws IOException {
        FeatureSnapshot s = resolve(environment(Map.of(), ceiling("awms.video.retention-days=30\n")));
        assertThat(s.preset()).isEqualTo(FeaturePreset.FULL);
        assertThat(s.presetSource()).isEqualTo("default");
        assertThat(s.ceilingFileExists()).isTrue();
        assertThat(s.warnings()).anySatisfy(w -> assertThat(w).contains("нет ключа awms.features.preset").contains("full"));
        // Сузить набор — явным пресетом в файле
        assertThat(resolve(environment(Map.of(), ceiling("awms.features.preset=isolated\n"))).isOn(Feature.VIDEO))
                .isFalse();
    }

    @Test
    void standardPresetFromFileWithOrigin() throws IOException {
        Path file = ceiling("awms.features.preset=standard\n");
        FeatureSnapshot s = resolve(environment(Map.of(), file));
        assertThat(s.preset()).isEqualTo(FeaturePreset.STANDARD);
        assertThat(s.presetSource()).isEqualTo("file");
        assertThat(s.isOn(Feature.MAP_PLAN)).isTrue();
        assertThat(s.isOn(Feature.MODEL3D_UPLOAD)).isTrue();
        assertThat(s.isOn(Feature.VIDEO)).isFalse();
        assertThat(s.isOn(Feature.ORDERS)).isFalse();
        assertThat(s.isOn(Feature.WEBSOCKET)).isFalse();
        assertThat(s.state(Feature.VIDEO).reason()).isEqualTo(FeatureState.Reason.PRESET);
        assertThat(s.state(Feature.VIDEO).origin()).isEqualTo("пресет standard");
        assertThat(s.warnings()).isEmpty();
        assertThat(s.ceilingFileSha256()).hasSize(64);
    }

    @Test
    void fullPresetMatchesTableAndKeepsServiceKeysOff() throws IOException {
        FeatureSnapshot s = resolve(environment(Map.of(), ceiling("awms.features.preset=full\n")));
        for (Feature f : Feature.values()) {
            assertThat(s.isOn(f)).as(f.key()).isEqualTo(f.isOnIn(FeaturePreset.FULL));
        }
        // По проекту выключены во всех пресетах: служебное, унаследованное и сохранение пароля браузером
        assertThat(s.isOn(Feature.TESTSYNC)).isFalse();
        assertThat(s.isOn(Feature.LEGACY)).isFalse();
        assertThat(s.isOn(Feature.PHOTOIMPORT)).isFalse();
        assertThat(s.isOn(Feature.PASSWORDS)).isFalse();
        assertThat(s.warnings()).isEmpty();
    }

    @Test
    void explicitKeyOverridesPresetAndCascadesToChildren() throws IOException {
        Path file = ceiling("""
                awms.features.preset=full
                awms.features.video.enabled=false
                awms.features.testsync.enabled=true
                """);
        FeatureSnapshot s = resolve(environment(Map.of(), file));
        assertThat(s.state(Feature.VIDEO).reason()).isEqualTo(FeatureState.Reason.PROPERTY);
        assertThat(s.state(Feature.VIDEO).origin()).isEqualTo("файл " + file.getFileName() + ":2");
        assertThat(s.state(Feature.VIDEO_LIVE).enabled()).isFalse();
        assertThat(s.state(Feature.VIDEO_LIVE).reasonCode()).isEqualTo("DEPENDENCY:video");
        // video.clips зависит и от video.live, и от video.download — причина первая по порядку (родитель)
        assertThat(s.state(Feature.VIDEO_CLIPS).reasonCode()).isEqualTo("DEPENDENCY:video");
        assertThat(s.warnings()).anySatisfy(w -> assertThat(w).contains("video.live").contains("video"));
        assertThat(s.isOn(Feature.TESTSYNC)).isTrue();
    }

    @Test
    void requiresSwitchesOffDependentFeature() throws IOException {
        FeatureSnapshot s = resolve(environment(Map.of(), ceiling("""
                awms.features.preset=standard
                awms.features.uploads.enabled=false
                """)));
        assertThat(s.isOn(Feature.MODEL3D)).isTrue();
        assertThat(s.state(Feature.MODEL3D_UPLOAD).reasonCode()).isEqualTo("DEPENDENCY:uploads");
    }

    @Test
    void rolesAreNormalizedAndChangeFingerprint() throws IOException {
        FeatureSnapshot plain = resolve(environment(Map.of(), ceiling("awms.features.preset=standard\n")));
        FeatureSnapshot withRoles = resolve(environment(Map.of(), ceiling("""
                awms.features.preset=standard
                awms.features.export.roles=director, ROLE_admin
                """)));
        assertThat(withRoles.roles(Feature.EXPORT)).containsExactly("ADMIN", "DIRECTOR");
        assertThat(withRoles.featuresWithRoles()).containsExactly(Feature.EXPORT);
        assertThat(withRoles.fingerprint()).isNotEqualTo(plain.fingerprint());
    }

    // ---------- Опечатки и чужие ключи ----------

    @Test
    void unknownFeatureKeyInFileFailsStartup() throws IOException {
        Path file = ceiling("awms.features.preset=full\nawms.features.vidoe.enabled=true\n");
        assertThatThrownBy(() -> resolve(environment(Map.of(), file)))
                .isInstanceOf(FeatureConfigurationException.class)
                .hasMessageContaining("vidoe");
    }

    @Test
    void unknownAttributeAndPresetFailStartup() throws IOException {
        Path wrongAttribute = ceiling("awms.features.preset=full\nawms.features.video.enable=true\n");
        assertThatThrownBy(() -> resolve(environment(Map.of(), wrongAttribute)))
                .isInstanceOf(FeatureConfigurationException.class).hasMessageContaining("enabled");
        Path wrongPreset = ceiling("awms.features.preset=fulll\n");
        assertThatThrownBy(() -> resolve(environment(Map.of(), wrongPreset)))
                .isInstanceOf(FeatureConfigurationException.class).hasMessageContaining("fulll");
        Path wrongValue = ceiling("awms.features.preset=full\nawms.features.video.enabled=yes\n");
        assertThatThrownBy(() -> resolve(environment(Map.of(), wrongValue)))
                .isInstanceOf(FeatureConfigurationException.class).hasMessageContaining("true или false");
    }

    @Test
    void foreignKeysInCeilingFileFailStartup() throws IOException {
        for (String foreign : List.of("jwt.cookie-token-key=x", "spring.config.import=optional:file:./other.properties",
                "logging.level.root=DEBUG", "spring.datasource.password=x")) {
            Path file = ceiling("awms.features.preset=full\n" + foreign + "\n");
            String key = foreign.substring(0, foreign.indexOf('='));
            assertThatThrownBy(() -> resolve(environment(Map.of(), file)))
                    .as(key)
                    .isInstanceOf(FeatureConfigurationException.class)
                    .hasMessageContaining(key)
                    .hasMessageContaining("белого списка");
        }
    }

    @Test
    void placeholdersInCeilingValuesAreRejected() throws IOException {
        Path file = ceiling("awms.features.preset=full\nawms.network.outbound.allowed=${OUTBOUND}\n");
        assertThatThrownBy(() -> resolve(environment(Map.of("OUTBOUND", "evil:443"), file)))
                .isInstanceOf(FeatureConfigurationException.class)
                .hasMessageContaining("подстановки");
    }

    @Test
    void absentFeatureCannotBeEnabled() throws IOException {
        Path file = ceiling("awms.features.preset=full\nawms.features.ai.enabled=true\n");
        assertThatThrownBy(() -> resolve(environment(Map.of(), file)))
                .isInstanceOf(FeatureConfigurationException.class)
                .hasMessageContaining("ai").hasMessageContaining("отсутствует в продукте");
        FeatureSnapshot off = resolve(environment(Map.of(), ceiling("awms.features.preset=full\nawms.features.ai.enabled=false\n")));
        assertThat(off.state(Feature.AI).reason()).isEqualTo(FeatureState.Reason.ABSENT);
    }

    // ---------- Переопределение потолка вне файла ----------

    @Test
    void environmentVariableWithTypoIsNamedInMessage() {
        StandardEnvironment env = environment(Map.of("AWMS_FEATURES_VIDOE_ENABLED", "true"), dir.resolve("missing.properties"));
        assertThatThrownBy(() -> resolve(env))
                .isInstanceOf(FeatureConfigurationException.class)
                .hasMessageContaining("AWMS_FEATURES_VIDOE_ENABLED")
                .hasMessageContaining("vidoe");
    }

    @Test
    void environmentVariableCannotOverrideCeiling() throws IOException {
        Path file = ceiling("awms.features.preset=full\n");
        assertThatThrownBy(() -> resolve(environment(Map.of("AWMS_FEATURES_VIDEO_ENABLED", "false"), file)))
                .isInstanceOf(FeatureConfigurationException.class)
                .hasMessageContaining("AWMS_FEATURES_VIDEO_ENABLED");
        // Даже без файла: переменные AWMS_FEATURES_* (кроме FILE и PRESET) не принимаются
        assertThatThrownBy(() -> resolve(environment(Map.of("AWMS_FEATURES_EXPORT_ROLES", "ADMIN"),
                dir.resolve("missing.properties"))))
                .isInstanceOf(FeatureConfigurationException.class)
                .hasMessageContaining("AWMS_FEATURES_EXPORT_ROLES");
    }

    @Test
    void presetVariableOnlyWithoutFile() throws IOException {
        FeatureSnapshot dev = resolve(environment(Map.of("AWMS_FEATURES_PRESET", "full"), dir.resolve("missing.properties")));
        assertThat(dev.preset()).isEqualTo(FeaturePreset.FULL);
        assertThat(dev.presetSource()).isEqualTo("env");
        assertThat(dev.warnings()).anySatisfy(w -> assertThat(w).contains("непригоден"));

        Path file = ceiling("awms.features.preset=standard\n");
        assertThatThrownBy(() -> resolve(environment(Map.of("AWMS_FEATURES_PRESET", "full"), file)))
                .isInstanceOf(FeatureConfigurationException.class)
                .hasMessageContaining("AWMS_FEATURES_PRESET");
    }

    @Test
    void securityParametersFromEnvironmentPropertiesOrArgumentsAreWarnedNotRejected(CapturedOutput output)
            throws IOException {
        // Как до выключателей: служба может задавать порт и т. п. окружением или аргументами — запуск не ломается,
        // значение применяется, в лог идёт предупреждение с именем ключа и источником
        Path file = ceiling("awms.features.preset=full\n");
        FeatureSnapshot fromEnv = resolve(environment(Map.of("SERVER_PORT", "9000"), file));
        assertThat(fromEnv.param(CeilingParam.SERVER_PORT)).isEqualTo("9000");
        assertThat(fromEnv.paramOrigin(CeilingParam.SERVER_PORT)).contains("SERVER_PORT");
        assertThat(output.getAll()).contains("WARN").contains("server.port").contains("SERVER_PORT");

        FeatureSnapshot retention = resolve(environment(Map.of("AWMS_VIDEO_RETENTION_DAYS", "5"), file));
        assertThat(retention.intParam(CeilingParam.VIDEO_RETENTION_DAYS)).isEqualTo(5);
        assertThat(output.getAll()).contains("awms.video.retention-days");

        Properties system = new Properties();
        system.setProperty("server.port", "9999");
        FeatureSnapshot property = resolve(environment(Map.of(), file,
                new PropertiesPropertySource(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, system)));
        assertThat(property.param(CeilingParam.SERVER_PORT)).isEqualTo("9999");
        assertThat(output.getAll()).contains("-Dserver.port");

        FeatureSnapshot argument = resolve(environment(Map.of(), file,
                new SimpleCommandLinePropertySource("--server.port=9100")));
        assertThat(argument.param(CeilingParam.SERVER_PORT)).isEqualTo("9100");
        assertThat(output.getAll()).contains("--server.port");
        // Значение входит в отпечаток службы, а print-features его не видит: предупреждение — и в профиль (шапка
        // отчёта), один раз на ключ
        assertThat(argument.fingerprint()).isNotEqualTo(resolve(environment(Map.of(), file)).fingerprint());
        assertThat(argument.warnings()).filteredOn(w -> w.contains("server.port")).singleElement()
                .satisfies(w -> assertThat(w).contains("--server.port").contains("отпечатки разойдутся"));

        // Выключатели функций окружением и аргументами — по-прежнему отказ запуска
        assertThatThrownBy(() -> resolve(environment(Map.of(), file,
                new SimpleCommandLinePropertySource("--awms.features.video.enabled=false"))))
                .isInstanceOf(FeatureConfigurationException.class)
                .hasMessageContaining("аргумент командной строки");
        Properties feature = new Properties();
        feature.setProperty("awms.features.video.enabled", "false");
        assertThatThrownBy(() -> resolve(environment(Map.of(), file,
                new PropertiesPropertySource(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, feature))))
                .isInstanceOf(FeatureConfigurationException.class)
                .hasMessageContaining("awms.features.video.enabled");
    }

    @Test
    void placeholdersOutsideCeilingFileAreResolvedWithWarning(CapturedOutput output) throws IOException {
        // Правило сервера: параметр белого списка из аргументов или внешнего конфига — предупреждение, а не отказ,
        // и с подстановкой тоже (--server.port=${PORT:8084}); подстановку разрешает окружение, как Spring при запуске
        Path file = ceiling("awms.features.preset=full\n");
        FeatureSnapshot withPort = resolve(environment(Map.of("PORT", "9300"), file,
                new SimpleCommandLinePropertySource("--server.port=${PORT:8084}")));
        assertThat(withPort.param(CeilingParam.SERVER_PORT)).isEqualTo("9300");
        assertThat(withPort.warnings()).filteredOn(w -> w.contains("server.port")).singleElement()
                .satisfies(w -> assertThat(w).contains("--server.port").contains("вне файла потолка"));
        assertThat(output.getAll()).contains("WARN").contains("--server.port");

        FeatureSnapshot byDefault = resolve(environment(Map.of(), file,
                new SimpleCommandLinePropertySource("--server.port=${PORT:8084}", "--server.address=${ADDR:127.0.0.1}")));
        assertThat(byDefault.param(CeilingParam.SERVER_PORT)).isEqualTo("8084");
        assertThat(byDefault.param(CeilingParam.SERVER_ADDRESS)).isEqualTo("127.0.0.1");

        Path other = dir.resolve("external.properties");
        Files.writeString(other, "server.port=${PORT:8084}\nawms.cors.allowed-origins=${SITE:http://site.example}\n");
        PropertySource<?> external = new PropertiesPropertySourceLoader()
                .load("external", new FileSystemResource(other)).get(0);
        FeatureSnapshot fromFile = resolve(environment(Map.of("PORT", "9400"), file, external));
        assertThat(fromFile.param(CeilingParam.SERVER_PORT)).isEqualTo("9400");
        assertThat(fromFile.listParam(CeilingParam.CORS_ALLOWED_ORIGINS)).containsExactly("http://site.example");

        // Выключатели и файл потолка — по-прежнему без подстановок
        assertThatThrownBy(() -> resolve(environment(Map.of("X", "false"), file,
                new SimpleCommandLinePropertySource("--awms.features.video.enabled=${X}"))))
                .isInstanceOf(FeatureConfigurationException.class)
                .hasMessageContaining("awms.features.video.enabled");
        Path withPlaceholder = ceiling("awms.features.preset=full\nserver.port=${PORT:8084}\n");
        assertThatThrownBy(() -> resolve(environment(Map.of("PORT", "9300"), withPlaceholder)))
                .isInstanceOf(FeatureConfigurationException.class)
                .hasMessageContaining("подстановки");
    }

    @Test
    void otherConfigFileWithParameterIsWarnedButFeatureKeysAreRejected(CapturedOutput output) throws IOException {
        Path file = ceiling("awms.features.preset=full\n");
        Path other = dir.resolve("application.properties");
        Files.writeString(other, "awms.cors.allowed-origins=http://extra.example\n");
        PropertySource<?> otherSource = new PropertiesPropertySourceLoader()
                .load("external", new FileSystemResource(other)).get(0);
        FeatureSnapshot s = resolve(environment(Map.of(), file, otherSource));
        assertThat(s.listParam(CeilingParam.CORS_ALLOWED_ORIGINS)).containsExactly("http://extra.example");
        assertThat(output.getAll()).contains("WARN").contains("awms.cors.allowed-origins");

        Path withFeature = dir.resolve("other-features.properties");
        Files.writeString(withFeature, "awms.features.video.enabled=false\n");
        PropertySource<?> featureSource = new PropertiesPropertySourceLoader()
                .load("external", new FileSystemResource(withFeature)).get(0);
        assertThatThrownBy(() -> resolve(environment(Map.of(), file, featureSource)))
                .isInstanceOf(FeatureConfigurationException.class)
                .hasMessageContaining("awms.features.video.enabled");
    }

    @Test
    void nonEnumerableSourceCannotCarryFeatureKeys(CapturedOutput output) throws IOException {
        // Источник без перечня имён (как JNDI): verify() его не перебирает, действующее значение проверяет find()
        Path file = ceiling("awms.features.preset=full\n");
        PropertySource<Object> jndiLike = jndiLike("awms.features.video.enabled", "false");
        assertThatThrownBy(() -> resolve(environment(Map.of(), file, jndiLike)))
                .isInstanceOf(FeatureConfigurationException.class)
                .hasMessageContaining("awms.features.video.enabled")
                .hasMessageContaining("jndiProperties");
        // Параметр безопасности из такого источника — как из окружения: применяется с предупреждением
        FeatureSnapshot port = resolve(environment(Map.of(), file, jndiLike("server.port", "9200")));
        assertThat(port.param(CeilingParam.SERVER_PORT)).isEqualTo("9200");
        assertThat(output.getAll()).contains("server.port").contains("jndiProperties");
    }

    private static PropertySource<Object> jndiLike(String key, String value) {
        return new PropertySource<>("jndiProperties", new Object()) {
            @Override
            public Object getProperty(String name) {
                return key.equals(name) ? value : null;
            }
        };
    }

    @Test
    void ffprobePathIsCeilingParameter(CapturedOutput output) throws IOException {
        Path file = ceiling("awms.features.preset=full\n");
        // Из окружения — применяется с предупреждением (как остальные параметры белого списка), источник виден в отчёте
        FeatureSnapshot fromEnv = resolve(environment(Map.of("VIDEO_FFPROBE_PATH", "C:/other/ffprobe.exe"), file));
        assertThat(fromEnv.param(CeilingParam.VIDEO_FFPROBE_PATH)).isEqualTo("C:/other/ffprobe.exe");
        assertThat(fromEnv.paramOrigin(CeilingParam.VIDEO_FFPROBE_PATH)).contains("VIDEO_FFPROBE_PATH");
        assertThat(output.getAll()).contains("video.ffprobe-path").contains("VIDEO_FFPROBE_PATH");
        FeatureSnapshot byDefault = resolve(environment(Map.of(), file));
        assertThat(byDefault.param(CeilingParam.VIDEO_FFPROBE_PATH)).isEmpty();
        FeatureSnapshot fromFile = resolve(environment(Map.of(), ceiling(
                "awms.features.preset=full\nvideo.ffprobe-path=D:/ffmpeg/bin/ffprobe.exe\n")));
        assertThat(fromFile.param(CeilingParam.VIDEO_FFPROBE_PATH)).isEqualTo("D:/ffmpeg/bin/ffprobe.exe");
        assertThat(fromFile.fingerprint()).isNotEqualTo(byDefault.fingerprint());
    }

    @Test
    void testContextPropertiesAreAccepted() {
        StandardEnvironment env = environment(Map.of(), dir.resolve("missing.properties"),
                new MapPropertySource("Inlined Test Properties", Map.of("awms.features.preset", "standard",
                        "server.port", "0")));
        FeatureSnapshot s = resolve(env);
        assertThat(s.preset()).isEqualTo(FeaturePreset.STANDARD);
        assertThat(s.presetSource()).isEqualTo("test");
        assertThat(s.param(CeilingParam.SERVER_PORT)).isEqualTo("0");
    }

    // ---------- Параметры безопасности ----------

    @Test
    void parametersHaveDefaultsJarValuesAndValidation() throws IOException {
        FeatureSnapshot s = resolve(environment(Map.of(), ceiling("awms.features.preset=full\n")));
        assertThat(s.intParam(CeilingParam.VIDEO_RETENTION_DAYS)).isEqualTo(30);
        assertThat(s.param(CeilingParam.ADMIN_MODE)).isEqualTo("disable-only");
        assertThat(s.param(CeilingParam.ADMIN_DISABLE_ROLES)).isEqualTo("ADMIN,SECURITY");
        // Как до выключателей: пустые списки — без ограничений, WebSocket — «*» (своего сужения нет, чужой Origin
        // отсекает CORS Spring Security), дополнительных адресов CORS нет (прежний список — в SecurityConfig)
        assertThat(s.param(CeilingParam.NETWORK_OUTBOUND_ALLOWED)).isEmpty();
        assertThat(s.param(CeilingParam.VIDEO_ALLOWED_NETWORKS)).isEmpty();
        assertThat(s.listParam(CeilingParam.CORS_ALLOWED_ORIGINS)).isEmpty();
        assertThat(s.listParam(CeilingParam.WEBSOCKET_ALLOWED_ORIGINS)).containsExactly("*");
        assertThat(s.paramOrigin(CeilingParam.WEBSOCKET_ALLOWED_ORIGINS)).contains("адреса CORS и тот же источник");
        // Явный список в файле потолка ограничивает
        FeatureSnapshot ws = resolve(environment(Map.of(), ceiling(
                "awms.features.preset=full\nawms.websocket.allowed-origins=https://awms.zavod.local\n")));
        assertThat(ws.listParam(CeilingParam.WEBSOCKET_ALLOWED_ORIGINS)).containsExactly("https://awms.zavod.local");
        assertThat(ws.paramOrigin(CeilingParam.WEBSOCKET_ALLOWED_ORIGINS)).startsWith("файл ");
        // Из умолчаний jar
        assertThat(s.param(CeilingParam.SERVER_PORT)).isEqualTo("8084");
        assertThat(s.paramOrigin(CeilingParam.SERVER_PORT)).isEqualTo("jar: умолчание");
        assertThat(s.paramOrigin(CeilingParam.VIDEO_RETENTION_DAYS)).isEqualTo("умолчание");

        FeatureSnapshot port = resolve(environment(Map.of(), ceiling("awms.features.preset=full\nserver.port=9090\n")));
        assertThat(port.param(CeilingParam.SERVER_PORT)).isEqualTo("9090");
        assertThat(port.paramOrigin(CeilingParam.SERVER_PORT)).startsWith("файл ");

        for (String bad : List.of("awms.video.retention-days=0", "awms.video.retention-days=forever",
                "awms.features.admin.mode=maybe", "awms.video.allow-file-source=1",
                "awms.features.admin.mode=full")) {
            Path file = ceiling("awms.features.preset=full\n" + bad + "\n");
            assertThatThrownBy(() -> resolve(environment(Map.of(), file))).as(bad)
                    .isInstanceOf(FeatureConfigurationException.class);
        }
        FeatureSnapshot full = resolve(environment(Map.of(), ceiling("""
                awms.features.preset=full
                awms.features.admin.mode=full
                awms.features.admin.roles=admin
                """)));
        assertThat(full.param(CeilingParam.ADMIN_ROLES)).isEqualTo("ADMIN");
    }

    @Test
    void ticketInUtf8IsReadCorrectly() throws IOException {
        Path file = ceiling("awms.features.preset=standard\nawms.features.ticket=СЗ-ИБ-2026-041 от 14.10.2026\n");
        assertThat(resolve(environment(Map.of(), file)).ticket()).isEqualTo("СЗ-ИБ-2026-041 от 14.10.2026");
    }

    // ---------- Отпечаток ----------

    @Test
    void fingerprintDependsOnValuesNotOnOrder() throws IOException {
        String a = "awms.features.preset=standard\nawms.cors.allowed-origins=http://a,http://b\nawms.video.retention-days=30\n";
        String b = "awms.video.retention-days=30\nawms.cors.allowed-origins=http://b, http://a\nawms.features.preset=standard\n";
        FeatureSnapshot first = resolve(environment(Map.of(), ceiling(a)));
        FeatureSnapshot second = resolve(environment(Map.of(), ceiling(b)));
        assertThat(first.fingerprint()).hasSize(64).isEqualTo(second.fingerprint());
        assertThat(first.revision()).isEqualTo(first.fingerprint().substring(0, 12) + "-0");

        FeatureSnapshot retention = resolve(environment(Map.of(), ceiling(a.replace("=30", "=31"))));
        assertThat(retention.fingerprint()).isNotEqualTo(first.fingerprint());
        FeatureSnapshot outbound = resolve(environment(Map.of(), ceiling(a + "awms.network.outbound.allowed=host:443\n")));
        assertThat(outbound.fingerprint()).isNotEqualTo(first.fingerprint());
        FeatureSnapshot feature = resolve(environment(Map.of(), ceiling(a + "awms.features.export.enabled=false\n")));
        assertThat(feature.fingerprint()).isNotEqualTo(first.fingerprint());
    }

    @Test
    void cliResolutionGivesTheSameFingerprintAsService() throws IOException {
        Path file = ceiling("awms.features.preset=standard\nawms.features.export.roles=ADMIN\nserver.port=8090\n");
        FeatureSnapshot service = resolve(environment(Map.of(), file));
        FeatureSnapshot cli = FeatureResolver.resolveFile(file);
        assertThat(cli.fingerprint()).isEqualTo(service.fingerprint());
        assertThat(cli.ceilingFileSha256()).isEqualTo(service.ceilingFileSha256());
        assertThat(cli.presetSource()).isEqualTo("file");
    }

    // ---------- Сборка без модуля ----------

    @Test
    void missingMarkerClassMeansAbsentInBuild() throws IOException {
        String marker = Feature.VIDEO.marker();
        ClassLoader hiding = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(marker)) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
        };
        Path file = ceiling("awms.features.preset=full\nawms.features.video.enabled=true\n");
        StandardEnvironment env = environment(Map.of(), file);
        FeatureSnapshot s = FeatureResolver.resolve(env, FeatureResolver.ceilingPath(env), hiding);
        assertThat(s.state(Feature.VIDEO).reason()).isEqualTo(FeatureState.Reason.ABSENT_IN_BUILD);
        assertThat(s.state(Feature.VIDEO_LIVE).reasonCode()).isEqualTo("DEPENDENCY:video");
    }

    @Test
    void resolveIsCachedPerEnvironment() throws IOException {
        StandardEnvironment env = environment(Map.of(), ceiling("awms.features.preset=full\n"));
        FeatureSnapshot first = FeatureResolver.resolve(env);
        assertThat(FeatureResolver.resolve(env)).isSameAs(first);
        // Пока окружение живо, сборка мусора кэш не очищает: условия, сервис и SecurityConfig видят один снимок
        System.gc();
        assertThat(FeatureResolver.resolve(env)).isSameAs(first);
    }

    @Test
    void switchesOnlyNarrowAndChangeRevision() throws IOException {
        FeatureSnapshot ceiling = resolve(environment(Map.of(), ceiling("awms.features.preset=standard\n")));
        FeatureSnapshot switched = ceiling.withSwitches(java.util.Set.of(Feature.MAP_PLAN, Feature.WEBSOCKET),
                java.util.Set.of(), 3);
        assertThat(switched.state(Feature.MAP_PLAN).reason()).isEqualTo(FeatureState.Reason.SWITCH);
        assertThat(switched.state(Feature.MAP_EDIT).reasonCode()).isEqualTo("DEPENDENCY:map.plan");
        // Выключенное потолком переключатель не трогает, ceilingOnly не переключается
        assertThat(switched.state(Feature.WEBSOCKET).reason()).isEqualTo(FeatureState.Reason.PRESET);
        assertThat(switched.revision()).isEqualTo(ceiling.fingerprint().substring(0, 12) + "-3");
        assertThat(switched.ceiling()).isSameAs(ceiling);
    }

    // ---------- Вспомогательное ----------

    private Path ceiling(String content) throws IOException {
        Path file = Files.createTempFile(dir, "awms-features", ".properties");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static FeatureSnapshot resolve(StandardEnvironment env) {
        return FeatureResolver.resolve(env, FeatureResolver.ceilingPath(env), FeatureResolver.class.getClassLoader());
    }

    /**
     * Окружение как у службы: системные свойства и переменные окружения — только заданные тестом (путь к потолку —
     * переменной AWMS_FEATURES_FILE), затем обработка конфигурации Spring Boot (application.properties и импорт)
     */
    static StandardEnvironment environment(Map<String, String> variables, Path ceilingFile, PropertySource<?>... extra) {
        StandardEnvironment env = new StandardEnvironment();
        MutablePropertySources sources = env.getPropertySources();
        sources.replace(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
                new PropertiesPropertySource(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, new Properties()));
        Map<String, Object> vars = new HashMap<>(variables);
        if (ceilingFile != null) vars.put(FeatureResolver.FEATURES_FILE_VARIABLE, ceilingFile.toString());
        sources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, vars));
        for (PropertySource<?> source : extra) {
            if (sources.contains(source.getName())) sources.replace(source.getName(), source);
            else sources.addFirst(source);
        }
        new SystemEnvironmentPropertySourceEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());
        ConfigDataEnvironmentPostProcessor.applyTo(env);
        return env;
    }
}

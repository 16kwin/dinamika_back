package com.example.dinamika_back.features;

import com.example.dinamika_back.features.report.FeatureCli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CLI {@code print-features --config=<путь>} (раздел 9.1 проекта): печатает тот же отпечаток, что служба отдаёт
 * в {@code /api/features} (ревизия) и в отчёте; без файла — изолированный минимум; ошибка в файле — код 2 и причина,
 * как при запуске службы.
 */
class FeatureCliTest {

    private static final Pattern FINGERPRINT = Pattern.compile("Отпечаток:\\s+([0-9a-f]{64})");

    @Test
    void printsSameFingerprintAsService(@TempDir Path dir) throws Exception {
        Path ceiling = dir.resolve("awms-features.properties");
        Files.writeString(ceiling, """
                # Профиль для проверки CLI: комментарии по-русски, файл в UTF-8, как у администратора
                awms.features.preset=standard
                awms.features.ticket=SZ-IB-2026-041
                awms.features.model3d.enabled=false
                awms.features.export.roles=ADMIN,DIRECTOR
                awms.video.retention-days=45
                """, StandardCharsets.UTF_8);

        Result cli = run("print-features", "--config=" + ceiling);
        assertThat(cli.code()).as(cli.err()).isEqualTo(FeatureCli.OK);
        String printed = fingerprint(cli.out());

        // Служба: тот же файл через spring.config.import (AWMS_FEATURES_FILE), как при обычном запуске
        String service;
        String revision;
        String previous = System.getProperty(FeatureResolver.FEATURES_FILE_VARIABLE);
        System.setProperty(FeatureResolver.FEATURES_FILE_VARIABLE, ceiling.toString());
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(Empty.class)
                .web(WebApplicationType.NONE).logStartupInfo(false).run()) {
            FeatureService features = new FeatureService(context.getEnvironment());
            service = features.ceiling().fingerprint();
            revision = features.revision();
            assertThat(features.ceiling().presetSource()).isEqualTo("file");
        } finally {
            if (previous == null) System.clearProperty(FeatureResolver.FEATURES_FILE_VARIABLE);
            else System.setProperty(FeatureResolver.FEATURES_FILE_VARIABLE, previous);
        }
        assertThat(printed).isEqualTo(service);
        assertThat(revision).startsWith(printed.substring(0, 12));

        assertThat(cli.out()).contains("Пресет:        standard")
                .contains("SZ-IB-2026-041")
                .contains("SHA-256 файла: " + FeatureResolver.sha256(Files.readAllBytes(ceiling)))
                .contains("model3d")
                .contains("awms.video.retention-days = 45 (файл awms-features.properties:")
                .contains("роли ADMIN,DIRECTOR")
                .contains("Отсутствует в продукте: ai, telemetry");
    }

    @Test
    void withoutConfigUsesServicePath() {
        // Как у службы: AWMS_FEATURES_FILE (в тестах его задаёт surefire — тестовый потолок), иначе ./config
        String configured = System.getProperty(FeatureResolver.FEATURES_FILE_VARIABLE);
        Result cli = run("print-features");
        assertThat(cli.code()).as(cli.err()).isEqualTo(FeatureCli.OK);
        if (configured != null) {
            assertThat(cli.out()).contains("из " + FeatureResolver.FEATURES_FILE_VARIABLE);
            assertThat(fingerprint(cli.out())).isEqualTo(FeatureResolver.resolveFile(Path.of(configured)).fingerprint());
        }
    }

    @Test
    void missingFileMeansIsolated(@TempDir Path dir) {
        Result cli = run("print-features", "--config=" + dir.resolve("нет-файла.properties"));
        assertThat(cli.code()).isEqualTo(FeatureCli.OK);
        assertThat(cli.out()).contains("Файл не найден").contains("isolated");
        assertThat(fingerprint(cli.out())).isEqualTo(FeatureResolver.resolveFile(dir.resolve("нет-файла.properties"))
                .fingerprint());
    }

    @Test
    void invalidProfileFailsLikeServiceStartup(@TempDir Path dir) throws Exception {
        Path typo = dir.resolve("typo.properties");
        Files.writeString(typo, "awms.features.preset=full\nawms.features.vidoe.enabled=false\n");
        Result cli = run("print-features", "--config=" + typo);
        assertThat(cli.code()).isEqualTo(FeatureCli.INVALID_PROFILE);
        assertThat(cli.err()).contains("vidoe");

        Path foreign = dir.resolve("foreign.properties");
        Files.writeString(foreign, "awms.features.preset=full\njwt.cookie-token-key=x\n");
        assertThat(run("print-features", "--config=" + foreign).code()).isEqualTo(FeatureCli.INVALID_PROFILE);

        Path cors = dir.resolve("cors.properties");
        Files.writeString(cors, "awms.features.preset=full\nawms.cors.allowed-origins=*\n");
        Result star = run("print-features", "--config=" + cors);
        assertThat(star.code()).isEqualTo(FeatureCli.INVALID_PROFILE);
        assertThat(star.err()).contains("awms.cors.allowed-origins");
    }

    @Test
    void dispatcherRecognisesOnlyKnownCommands() {
        assertThat(FeatureCli.isCommand(new String[]{"print-features"})).isTrue();
        assertThat(FeatureCli.isCommand(new String[]{"help"})).isTrue();
        assertThat(FeatureCli.isCommand(new String[]{})).isFalse();
        assertThat(FeatureCli.isCommand(null)).isFalse();
        assertThat(FeatureCli.isCommand(new String[]{"--server.port=8084"})).isFalse();

        assertThat(run("print-features", "--verbose").code()).isEqualTo(FeatureCli.USAGE);
        assertThat(run("unknown").code()).isEqualTo(FeatureCli.USAGE);
        Result help = run("help");
        assertThat(help.code()).isEqualTo(FeatureCli.OK);
        assertThat(help.out()).contains("print-features [--config=");
    }

    // ===================== Вспомогательное =====================

    private static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = FeatureCli.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static String fingerprint(String out) {
        Matcher m = FINGERPRINT.matcher(out);
        assertThat(m.find()).as(out).isTrue();
        return m.group(1);
    }

    private record Result(int code, String out, String err) {
    }

    /** Пустой контекст: только окружение Spring Boot с application.properties и файлом потолка */
    @Configuration(proxyBeanMethods = false)
    static class Empty {
    }
}

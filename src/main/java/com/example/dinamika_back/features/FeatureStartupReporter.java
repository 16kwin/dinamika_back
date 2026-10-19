package com.example.dinamika_back.features;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * При готовности приложения выводит в лог (логгер {@code AWMS.SECURITY}) таблицу функций, пресет, отпечаток,
 * путь к файлу потолка и порт. Нет файла потолка — громкое предупреждение: работает изолированный минимум.
 */
@Component
@RequiredArgsConstructor
public class FeatureStartupReporter {

    private static final Logger SECURITY = LoggerFactory.getLogger("AWMS.SECURITY");

    private final FeatureService features;

    @EventListener(ApplicationReadyEvent.class)
    public void report(ApplicationReadyEvent event) {
        String port = "—";
        if (event.getApplicationContext() instanceof WebServerApplicationContext web) {
            WebServer server = web.getWebServer();
            if (server != null) port = String.valueOf(server.getPort());
        }
        FeatureSnapshot ceiling = features.ceiling();
        SECURITY.info(table(ceiling, port));
        if ("default".equals(ceiling.presetSource())) {
            SECURITY.warn("""

                    ****************************************************************************************
                    *  ПРОФИЛЬ ФУНКЦИЙ: ПРИМЕНЁН ИЗОЛИРОВАННЫЙ МИНИМУМ (isolated) — пресет в потолке не задан
                    *  Файл потолка: {}{}
                    *  Работает только ядро учёта. Это не поломка, а защита: положите файл потолка
                    *  (шаблоны — deploy/features) или задайте путь в AWMS_FEATURES_FILE и перезапустите службу
                    ****************************************************************************************""",
                    ceiling.ceilingFile(), ceiling.ceilingFileExists() ? "" : " (не найден)");
        }
        for (String warning : ceiling.warnings()) {
            SECURITY.warn(warning);
        }
    }

    /** Таблица профиля для лога: одна строка на функцию */
    static String table(FeatureSnapshot s, String port) {
        StringBuilder out = new StringBuilder();
        out.append("Профиль функций AWMS\n");
        out.append("  Пресет: ").append(s.preset().key()).append(" («").append(s.preset().title())
                .append("»), источник: ").append(presetSourceText(s.presetSource())).append('\n');
        out.append("  Файл потолка: ").append(s.ceilingFile())
                .append(s.ceilingFileExists() ? ", SHA-256 " + s.ceilingFileSha256() : " — не найден").append('\n');
        out.append("  Основание: ").append(s.ticket() == null ? "—" : s.ticket()).append('\n');
        out.append("  Отпечаток: ").append(s.fingerprint()).append(", ревизия ").append(s.revision()).append('\n');
        out.append("  Порт: ").append(port).append(", адрес: ")
                .append(emptyAsDash(s.param(CeilingParam.SERVER_ADDRESS))).append('\n');
        out.append("  Функции:\n");
        for (Feature f : Feature.real()) {
            FeatureState state = s.state(f);
            out.append(String.format("    %-20s %-5s %-4s %s", f.key(), state.enabled() ? "вкл" : "выкл",
                    f.level().name(), state.reasonText()));
            if (!state.roles().isEmpty()) out.append("; роли ").append(String.join(",", state.roles()));
            out.append('\n');
        }
        out.append("  Отсутствует в продукте: ").append(Arrays.stream(Feature.values()).filter(Feature::isAbsent)
                .map(Feature::key).collect(Collectors.joining(", "))).append('\n');
        out.append("  Параметры безопасности:\n");
        for (CeilingParam p : CeilingParam.values()) {
            out.append("    ").append(p.key()).append(" = ").append(emptyAsDash(s.param(p)))
                    .append(" (").append(s.paramOrigin(p)).append(")\n");
        }
        return out.toString();
    }

    private static String presetSourceText(String source) {
        return switch (source) {
            case "file" -> "файл потолка";
            case "env" -> "переменная окружения AWMS_FEATURES_PRESET (для согласования непригоден)";
            case "default" -> "умолчание (пресет не задан)";
            case "test" -> "тестовый контекст";
            default -> source;
        };
    }

    private static String emptyAsDash(String value) {
        return value == null || value.isEmpty() ? "—" : value;
    }
}

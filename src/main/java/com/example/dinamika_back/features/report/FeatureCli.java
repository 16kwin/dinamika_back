package com.example.dinamika_back.features.report;

import com.example.dinamika_back.config.security.SecurityConfig;
import com.example.dinamika_back.features.CeilingParam;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.FeatureConfigurationException;
import com.example.dinamika_back.features.FeatureResolver;
import com.example.dinamika_back.features.FeatureSnapshot;
import com.example.dinamika_back.features.FeatureState;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.Console;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Команды обслуживания: {@code java -jar dinamika_back.jar <команда> [параметры]} (раздел 9.1 проекта). Выполняются
 * вместо запуска службы — без веб-сервера и БД — и завершают процесс кодом возврата.
 *
 * <ul>
 *   <li>{@code print-features [--config=<путь>]} — профиль функций из файла потолка: таблица функций, пресет,
 *       параметры безопасности с источником каждого значения, SHA-256 файла и отпечаток. Выключатели служба
 *       принимает только из файла потолка и умолчаний jar, поэтому отпечаток совпадает с отчётом «Профиль
 *       безопасности» даже без доступа к окружению службы. Исключение — параметр безопасности, заданный службе
 *       аргументом или переменной окружения (совместимость с прежними службами): служба его применяет, отпечаток
 *       службы тогда другой, а отчёт называет такой параметр в предупреждениях. Те же проверки, что при запуске:
 *       ошибка в файле — код 2 и причина.</li>
 *   <li>{@code help} — список команд.</li>
 * </ul>
 * Команды {@code init-secrets} и {@code create-user} регистрирует пакет B10 в {@link #COMMANDS}.
 *
 * <p>Коды возврата: 0 — готово; 2 — профиль не пройдёт проверку при запуске службы; 64 — неверный вызов.
 */
public final class FeatureCli {

    public static final int OK = 0;
    public static final int INVALID_PROFILE = 2;
    public static final int USAGE = 64;

    /** Команда обслуживания */
    public interface Command {
        /** Строка вызова для справки: {@code print-features [--config=<путь>]} */
        String usage();

        String description();

        int run(List<String> args, PrintStream out, PrintStream err) throws Exception;
    }

    /** Команды по имени, в порядке справки */
    private static final Map<String, Command> COMMANDS = commands();

    private FeatureCli() {
    }

    private static Map<String, Command> commands() {
        Map<String, Command> commands = new LinkedHashMap<>();
        commands.put("print-features", new PrintFeatures());
        commands.put("help", new Help());
        return Collections.unmodifiableMap(commands);
    }

    /** Первый аргумент — команда обслуживания (иначе это запуск службы) */
    public static boolean isCommand(String[] args) {
        return args != null && args.length > 0 && args[0] != null && COMMANDS.containsKey(args[0]);
    }

    /** Из main: тихий лог (только предупреждения), без настройки логов Spring Boot — файлов логов команда не создаёт */
    public static int main(String[] args) {
        System.setProperty("org.springframework.boot.logging.LoggingSystem", "none");
        quietLogging();
        Charset charset = consoleCharset();
        PrintStream out = new PrintStream(System.out, true, charset);
        PrintStream err = new PrintStream(System.err, true, charset);
        return run(args, out, err);
    }

    /** Кодировка консоли (cp866 у cmd на русской Windows); вывод в файл — UTF-8 */
    private static Charset consoleCharset() {
        Console console = System.console();
        return console != null ? console.charset() : StandardCharsets.UTF_8;
    }

    /** Выполнить команду; вывод — в {@code out}, ошибки — в {@code err} */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (!isCommand(args)) {
            err.println("Неизвестная команда. Список команд: java -jar dinamika_back.jar help");
            return USAGE;
        }
        Command command = COMMANDS.get(args[0]);
        try {
            return command.run(Arrays.asList(args).subList(1, args.length), out, err);
        } catch (FeatureConfigurationException e) {
            err.println("Профиль функций не пройдёт проверку при запуске службы: " + e.getMessage());
            return INVALID_PROFILE;
        } catch (UsageException e) {
            err.println(e.getMessage());
            err.println("Вызов: java -jar dinamika_back.jar " + command.usage());
            return USAGE;
        } catch (Exception e) {
            err.println("Команда не выполнена: " + e.getMessage());
            return 1;
        }
    }

    private static void quietLogging() {
        try {
            Object factory = LoggerFactory.getILoggerFactory();
            if (factory instanceof ch.qos.logback.classic.LoggerContext context) {
                context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(ch.qos.logback.classic.Level.WARN);
            }
        } catch (RuntimeException | LinkageError e) {
            // Не logback — выводу команды это не мешает
        }
    }

    /** Неверные параметры команды */
    public static final class UsageException extends RuntimeException {
        public UsageException(String message) {
            super(message);
        }
    }

    // ===================== print-features =====================

    /** {@code print-features [--config=<путь>]} */
    static final class PrintFeatures implements Command {

        @Override
        public String usage() {
            return "print-features [--config=<путь к файлу потолка>]";
        }

        @Override
        public String description() {
            return "профиль функций и отпечаток из файла потолка без запуска службы (для сверки с отчётом)";
        }

        @Override
        public int run(List<String> args, PrintStream out, PrintStream err) {
            String config = null;
            for (String arg : args) {
                if (arg.startsWith("--config=") && arg.length() > "--config=".length()) {
                    if (config != null) throw new UsageException("Параметр --config указан дважды");
                    config = arg.substring("--config=".length()).trim();
                } else {
                    throw new UsageException("Неизвестный параметр: " + arg);
                }
            }
            Path file;
            String pathSource;
            if (config != null) {
                file = path(config);
                pathSource = "из параметра --config";
            } else {
                // Путь по умолчанию — как у службы: окружение команды разбирает тот же Spring Boot (без веба и БД)
                try (ConfigurableApplicationContext context = new SpringApplicationBuilder(FeatureCliConfiguration.class)
                        .web(WebApplicationType.NONE)
                        .bannerMode(Banner.Mode.OFF)
                        .logStartupInfo(false)
                        .registerShutdownHook(false)
                        .run()) {
                    file = FeatureResolver.ceilingPath(context.getEnvironment());
                    String variable = context.getEnvironment().getProperty(FeatureResolver.FEATURES_FILE_VARIABLE);
                    pathSource = variable == null || variable.isBlank() ? "по умолчанию (./config рядом с рабочим каталогом)"
                            : "из " + FeatureResolver.FEATURES_FILE_VARIABLE;
                }
            }
            FeatureSnapshot snapshot = FeatureResolver.resolveFile(file);
            // Те же проверки, что делает служба при запуске (SecurityConfig): адреса CORS
            SecurityConfig.corsOrigins(snapshot.listParam(CeilingParam.CORS_ALLOWED_ORIGINS));
            out.print(format(snapshot, pathSource));
            return OK;
        }

        private static Path path(String value) {
            try {
                return Paths.get(value).toAbsolutePath().normalize();
            } catch (InvalidPathException e) {
                throw new UsageException("Некорректный путь: " + value);
            }
        }
    }

    /** Текст профиля для консоли (и для сверки с бумагой) */
    static String format(FeatureSnapshot s, String pathSource) {
        StringBuilder out = new StringBuilder();
        out.append("Профиль функций AWMS — проверка файла потолка без запуска службы\n");
        out.append("  Файл потолка:  ").append(s.ceilingFile()).append(" (").append(pathSource).append(")\n");
        if (s.ceilingFileExists()) {
            out.append("  SHA-256 файла: ").append(s.ceilingFileSha256()).append('\n');
            out.append("  Изменён:       ").append(modified(s.ceilingFile())).append(", владелец: ")
                    .append(owner(s.ceilingFile())).append('\n');
        } else {
            out.append("  Файл не найден: служба применит пресет ").append(s.preset().key()).append(" («")
                    .append(s.preset().title()).append("»), как до выключателей\n");
        }
        out.append("  Пресет:        ").append(s.preset().key()).append(" («").append(s.preset().title()).append("»), ")
                .append(presetSource(s.presetSource(), s.ceilingFileExists())).append('\n');
        out.append("  Основание:     ").append(s.ticket() == null ? "—" : s.ticket()).append('\n');
        out.append("  Отпечаток:     ").append(s.fingerprint()).append('\n');
        out.append("  Ревизия:       ").append(s.revision()).append(" (без переключателей администратора)\n");
        out.append('\n');
        out.append("Функции (ключ, состояние, уровень, источник значения):\n");
        for (Feature f : Feature.real()) {
            FeatureState state = s.state(f);
            out.append(String.format("  %-20s %-5s %-3s %s", f.key(), state.enabled() ? "вкл" : "выкл", f.level().name(),
                    state.reasonText()));
            if (state.reason() == FeatureState.Reason.PRESET) out.append(" (").append(state.origin()).append(')');
            if (!state.roles().isEmpty()) out.append("; роли ").append(String.join(",", state.roles()));
            out.append('\n');
        }
        out.append("Отсутствует в продукте: ").append(Arrays.stream(Feature.values()).filter(Feature::isAbsent)
                .map(Feature::key).collect(Collectors.joining(", "))).append('\n');
        out.append('\n');
        out.append("Параметры безопасности (значение, источник):\n");
        for (CeilingParam p : CeilingParam.values()) {
            String value = display(p, s.param(p));
            out.append("  ").append(p.key()).append(" = ").append(value == null || value.isEmpty() ? "—" : value)
                    .append(" (").append(s.paramOrigin(p)).append(")\n");
            if (p == CeilingParam.CORS_ALLOWED_ORIGINS) out.append("    ").append(CeilingParam.CORS_NOTE).append('\n');
        }
        List<String> warnings = new ArrayList<>(s.warnings());
        if (!warnings.isEmpty()) {
            out.append('\n').append("Предупреждения:\n");
            for (String w : warnings) out.append("  ").append(w).append('\n');
        }
        out.append('\n');
        out.append("Сверка: отпечаток выше совпадает с шапкой отчёта «Профиль безопасности» и с записью STARTUP журнала,\n");
        out.append("если служба запущена с этим файлом. Выключатели awms.features.* окружением и аргументами не\n");
        out.append("переопределяются (служба с ними не запустится). Параметр безопасности, заданный службе аргументом или\n");
        out.append("переменной окружения, служба применяет с предупреждением — тогда её отпечаток другой, а отчёт называет\n");
        out.append("такой параметр в предупреждениях шапки.\n");
        return out.toString();
    }

    /** Значение параметра для печати: адрес внешней системы — без логина и пароля */
    static String display(CeilingParam p, String value) {
        if (p != CeilingParam.SYNC_SERVICE_URL || value == null || value.isEmpty()) return value;
        String safe = SecurityProfileService.withoutCredentials(value);
        return safe != null ? safe : "(адрес не разобран, не печатается)";
    }

    private static String modified(Path file) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault())
                    .withNano(0).toString().replace('T', ' ');
        } catch (IOException | RuntimeException e) {
            return "—";
        }
    }

    private static String owner(Path file) {
        try {
            return Files.getOwner(file).getName();
        } catch (IOException | RuntimeException e) {
            return "—";
        }
    }

    private static String presetSource(String source, boolean fileExists) {
        return switch (source) {
            case "file" -> "из файла потолка";
            case "default" -> fileExists ? "по умолчанию: пресет в файле не задан" : "по умолчанию: файла потолка нет";
            case "jar" -> "из умолчаний jar";
            default -> source;
        };
    }

    // ===================== help =====================

    /** {@code help} */
    static final class Help implements Command {

        @Override
        public String usage() {
            return "help";
        }

        @Override
        public String description() {
            return "список команд обслуживания";
        }

        @Override
        public int run(List<String> args, PrintStream out, PrintStream err) {
            out.println("Команды обслуживания AWMS (выполняются без запуска службы):");
            for (Command command : COMMANDS.values()) {
                out.println("  java -jar dinamika_back.jar " + command.usage());
                out.println("      " + command.description());
            }
            out.println("Без команды jar запускает службу.");
            return OK;
        }
    }
}

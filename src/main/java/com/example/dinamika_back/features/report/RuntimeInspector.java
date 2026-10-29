package com.example.dinamika_back.features.report;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Живое состояние приложения для самопроверок отчёта «Профиль безопасности» (раздел 9.2 проекта): какие бины созданы,
 * какие потоки и дочерние процессы есть, какие маршруты обслуживаются, чем запущена JVM. Только чтение: процессов
 * не запускает, аргументов дочерних процессов и JVM не отдаёт (в них могут быть адреса камер с паролями).
 */
@Component
public class RuntimeInspector {

    /** Хвост имени потока: номер или код пула — по нему потоки группируются в префиксы */
    private static final Pattern THREAD_TAIL = Pattern.compile("[-_#]?\\d+$");
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[^/]+}");

    private final ApplicationContext context;
    private final Environment environment;

    public RuntimeInspector(ApplicationContext context, Environment environment) {
        this.context = context;
        this.environment = environment;
    }

    /** Создан ли бин класса (класса нет в сборке — бина нет) */
    public boolean beanPresent(String className) {
        if (className == null || !ClassUtils.isPresent(className, getClass().getClassLoader())) return false;
        Class<?> type = ClassUtils.resolveClassName(className, getClass().getClassLoader());
        return beanCount(type) > 0;
    }

    /** Есть ли класс в сборке */
    public boolean classPresent(String className) {
        return className != null && ClassUtils.isPresent(className, getClass().getClassLoader());
    }

    /** Сколько бинов данного типа (без создания ленивых) */
    public int beanCount(Class<?> type) {
        ListableBeanFactory beans = context;
        return beans.getBeanNamesForType(type, true, false).length;
    }

    /** Живые потоки, имя которых начинается с префикса */
    public int threadsWithPrefix(String prefix) {
        int count = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().startsWith(prefix)) count++;
        }
        return count;
    }

    /** Потоки приложения по префиксам имени (номер в конце отбрасывается), по алфавиту */
    public Map<String, Integer> threadsByPrefix() {
        Map<String, Integer> result = new TreeMap<>();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            String prefix = THREAD_TAIL.matcher(t.getName()).replaceFirst("");
            if (prefix.isEmpty()) prefix = t.getName();
            result.merge(prefix, 1, Integer::sum);
        }
        return result;
    }

    /** Имена исполняемых файлов дочерних процессов JVM (без аргументов) */
    public List<String> childProcesses() {
        List<String> result = new ArrayList<>();
        ProcessHandle.current().descendants().forEach(p -> result.add(p.info().command()
                .map(c -> {
                    Path name = Path.of(c).getFileName();
                    return name == null ? c : name.toString();
                })
                .orElse("pid " + p.pid())));
        result.sort(String::compareTo);
        return result;
    }

    /** Фактический порт веб-сервера или null (контекст без веб-сервера) */
    public Integer port() {
        if (context instanceof WebServerApplicationContext web && web.getWebServer() != null) {
            int port = web.getWebServer().getPort();
            if (port > 0) return port;
        }
        String local = environment.getProperty("local.server.port");
        return local == null ? null : Integer.valueOf(local);
    }

    /**
     * Адреса CORS, с которыми служба работает на самом деле: прежний список SecurityConfig и строка потолка
     * {@code awms.cors.allowed-origins} — она только добавляет к прежнему списку. Пусто — источник CORS не найден
     */
    public List<String> corsOrigins() {
        for (CorsConfigurationSource source : context.getBeansOfType(CorsConfigurationSource.class).values()) {
            if (source instanceof UrlBasedCorsConfigurationSource url) {
                CorsConfiguration all = url.getCorsConfigurations().get("/**");
                if (all != null && all.getAllowedOrigins() != null) return List.copyOf(all.getAllowedOrigins());
            }
        }
        return List.of();
    }

    /** Маршруты контроллеров: шаблон пути и методы (пусто — любой метод) */
    public List<Route> routes() {
        Set<Route> result = new LinkedHashSet<>();
        for (RequestMappingHandlerMapping mapping : context.getBeansOfType(RequestMappingHandlerMapping.class).values()) {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
                RequestMappingInfo info = entry.getKey();
                List<String> methods = info.getMethodsCondition().getMethods().stream().map(Enum::name).sorted().toList();
                for (String pattern : info.getPatternValues()) {
                    result.add(new Route(pattern, methods, entry.getValue().getBeanType().getSimpleName()));
                }
            }
        }
        List<Route> sorted = new ArrayList<>(result);
        sorted.sort((a, b) -> a.pattern().equals(b.pattern()) ? String.join(",", a.methods())
                .compareTo(String.join(",", b.methods())) : a.pattern().compareTo(b.pattern()));
        return sorted;
    }

    /** Конкретный адрес по шаблону маршрута: {@code {id}} → 1, {@code **} → x, {@code *} → 1 */
    public static String samplePath(String pattern) {
        return PATH_VARIABLE.matcher(pattern).replaceAll("1").replace("**", "x").replace("*", "1");
    }

    /** Префикс маршрута для таблицы: первые два сегмента ({@code /api/nomenclature}) */
    public static String routePrefix(String pattern) {
        String[] parts = pattern.split("/");
        StringBuilder prefix = new StringBuilder();
        int taken = 0;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (part.startsWith("{") || part.contains("*")) break;
            prefix.append('/').append(part);
            if (++taken == 2) break;
        }
        return prefix.length() == 0 ? "/" : prefix.toString();
    }

    /**
     * Проверки среды (раздел 9.2, п. 3): нет actuator, devtools и перезагрузки конфигурации на ходу, JVM запущена без
     * отладчика (JDWP) и без удалённого JMX. Аргументы JVM не отдаются — только вывод
     */
    public List<Check> runtimeChecks() {
        List<Check> checks = new ArrayList<>();
        checks.add(new Check("actuator (служебные эндпоинты Spring Boot) в сборке отсутствует",
                !classPresent("org.springframework.boot.actuate.endpoint.annotation.Endpoint")));
        checks.add(new Check("devtools (перезапуск и удалённое обновление) в сборке отсутствует",
                !classPresent("org.springframework.boot.devtools.restart.Restarter")));
        checks.add(new Check("@RefreshScope (смена настроек на ходу) в сборке отсутствует",
                !classPresent("org.springframework.cloud.context.config.annotation.RefreshScope")));
        List<String> args = jvmArguments();
        boolean jdwp = args.stream().anyMatch(a -> a.startsWith("-agentlib:jdwp") || a.startsWith("-Xrunjdwp")
                || a.equals("-Xdebug"));
        checks.add(new Check("JVM запущена без отладчика (JDWP)", !jdwp));
        boolean jmxRemote = args.stream().anyMatch(a -> a.startsWith("-Dcom.sun.management.jmxremote.port")
                || a.startsWith("-Dcom.sun.management.jmxremote.rmi.port"))
                || System.getProperty("com.sun.management.jmxremote.port") != null;
        checks.add(new Check("удалённый JMX не открыт (com.sun.management.jmxremote.port не задан)", !jmxRemote));
        return checks;
    }

    private static List<String> jvmArguments() {
        try {
            return List.copyOf(ManagementFactory.getRuntimeMXBean().getInputArguments());
        } catch (RuntimeException | Error e) {
            return List.of();
        }
    }

    /** Число задач {@code @Scheduled} (0, если планирование не включено) */
    public int scheduledTasks() {
        String name = "org.springframework.context.annotation.internalScheduledAnnotationProcessor";
        if (!context.containsBean(name)) return 0;
        Object processor = context.getBean(name);
        if (processor instanceof org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor p) {
            return p.getScheduledTasks().size();
        }
        return 0;
    }

    /** Маршрут контроллера */
    public record Route(String pattern, List<String> methods, String controller) {
    }

    /** Самопроверка: текст и итог (true — как ожидается, false — внимание, null — для сведения) */
    public record Check(String text, Boolean ok) {
    }
}

package com.example.dinamika_back.config.security;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Таблица «метод + путь → ответ защиты» для всех маршрутов контроллеров и прежних адресов без входа, по столбцам:
 * аноним, аноним с чужого адреса с подложным X-Forwarded-For, OPERATOR, DIRECTOR, ADMIN и (для чтения) SECURITY.
 * Для изменяющих методов в ячейке два ответа: без CSRF-токена | с токеном.
 *
 * <p>Эталон {@link #GOLDEN} снят с SecurityConfig в том виде, в каком он был до слоя правил потолка (main 7a1e0f0,
 * версия 0.4.4): без файла потолка и при full таблица обязана совпасть с ним строка в строку — так доказано, что
 * слой потолка ничего не меняет на сервере команды. Маршруты, которых в эталоне нет (появились позже), только
 * перечисляются: их правила те же, слой потолка общий для всех путей.
 *
 * <p>Если правила доступа изменены намеренно — пересоздать эталон:
 * {@code ./mvnw -o test -Dtest=AccessMatrixFullTest -Dawms.test.record-access-matrix=true}
 */
final class AccessMatrix {

    /** Эталон: src/test/resources/security/access-matrix-main.txt (рабочий каталог тестов — корень бэкенда) */
    static final Path GOLDEN = Paths.get("src", "test", "resources", "security", "access-matrix-main.txt");
    static final String RECORD_PROPERTY = "awms.test.record-access-matrix";

    private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final List<String> ALL_METHODS = List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE");

    /**
     * Адреса вне контроллеров: прежние правила без входа (в том числе без маршрута), раздача файлов, SockJS,
     * служебные адреса, профиль безопасности и неизвестный путь
     */
    private static final List<String> EXTRA = List.of(
            "GET /uploads/nomenclature/1/photo.png", "HEAD /uploads/nomenclature/1/photo.png",
            "POST /uploads/x", "DELETE /uploads/x",
            "GET /ws-stations/info", "HEAD /ws-stations/info", "POST /ws-stations/123/abcdef/xhr_send",
            "POST /ws-stations/123/abcdef/xhr_streaming", "GET /ws-stations/123/abcdef/websocket",
            "GET /api/locations/hierarchy/first", "GET /api/locations/1/photo", "POST /api/locations/1/photo",
            "DELETE /api/locations/1/photo", "POST /api/locations", "POST /api/stations",
            "GET /api/users/1", "POST /api/users/1", "GET /api/notification/1", "POST /api/notification/1",
            "POST /api/animal_card/1", "PATCH /api/animal_card/1", "DELETE /api/animal_card/1", "GET /api/animal_card/1",
            "POST /api/docs/1", "GET /api/docs/1", "POST /api/information/1", "GET /api/information/1",
            "GET /api/user/filters", "POST /api/user/filters", "DELETE /api/user/filters",
            "POST /api/sync/receive", "GET /api/sync/receive", "GET /api/test-documents/1", "POST /api/test-documents/1",
            "GET /csrf", "HEAD /csrf", "POST /logout", "GET /logout", "GET /error", "POST /error",
            "POST /api/auth/login", "POST /api/auth/refresh_token", "POST /api/auth/check_password",
            "GET /api/auth/x/y", "GET /api/features/public", "HEAD /api/features/public", "GET /api/features",
            "GET /api/security-profile", "POST /api/security-profile/events", "PUT /api/admin/features",
            "GET /api/unknown", "POST /api/unknown", "GET /", "GET /index.html", "GET /images/x.png",
            "GET /proc_docs/x.pdf");

    /** Столбцы таблицы */
    private enum Who {
        ANON("anon"), EXT("ext"), OPERATOR("OPERATOR"), DIRECTOR("DIRECTOR"), ADMIN("ADMIN"), SECURITY("SECURITY");

        final String label;

        Who(String label) {
            this.label = label;
        }
    }

    private AccessMatrix() {
    }

    /** Все запросы таблицы: «МЕТОД /путь», по алфавиту */
    static Set<String> requests() {
        Set<String> result = new TreeSet<>(EXTRA);
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        for (BeanDefinition bd : scanner.findCandidateComponents("com.example.dinamika_back")) {
            Class<?> type = ClassUtils.resolveClassName(bd.getBeanClassName(), AccessMatrix.class.getClassLoader());
            if (fromTestClasses(type)) continue;
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            String[] prefixes = base == null || base.path().length == 0 ? new String[]{""} : base.path();
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) continue;
                List<String> methods = new ArrayList<>();
                if (mapping.method().length == 0) {
                    methods.addAll(ALL_METHODS);
                } else {
                    for (RequestMethod m : mapping.method()) {
                        methods.add(m.name());
                        // Spring MVC обслуживает HEAD обработчиком GET
                        if (m == RequestMethod.GET) methods.add("HEAD");
                    }
                }
                String[] paths = mapping.path().length == 0 ? new String[]{""} : mapping.path();
                for (String prefix : prefixes) {
                    for (String path : paths) {
                        String full = prefix + (path.isEmpty() || path.startsWith("/") ? path : "/" + path);
                        if (!full.startsWith("/")) full = "/" + full;
                        String sample = full.replaceAll("\\{[^}]*}", "1").replace("**", "x/y").replace("*", "x");
                        for (String m : methods) {
                            if (m.equals("OPTIONS") || m.equals("TRACE")) continue;
                            result.add(m + " " + sample);
                        }
                    }
                }
            }
        }
        return result;
    }

    private static boolean fromTestClasses(Class<?> type) {
        URL location = type.getProtectionDomain().getCodeSource() == null ? null
                : type.getProtectionDomain().getCodeSource().getLocation();
        return location != null && location.toString().contains("test-classes");
    }

    /** Строка таблицы для запроса «МЕТОД /путь» */
    static String row(MockMvc mvc, String request) {
        int space = request.indexOf(' ');
        String method = request.substring(0, space);
        String path = request.substring(space + 1);
        boolean write = WRITES.contains(method);
        StringBuilder row = new StringBuilder(request);
        for (Who who : Who.values()) {
            if (who == Who.SECURITY && write) continue;
            row.append(' ').append(who.label).append('=').append(status(mvc, method, path, who, false));
            if (write) row.append('|').append(status(mvc, method, path, who, true));
        }
        return row.toString();
    }

    /** Код ответа; исключение, вылетевшее из цепочки фильтров (в службе это 500), — ERR */
    private static String status(MockMvc mvc, String method, String path, Who who, boolean withCsrf) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path);
        switch (who) {
            case ANON -> {
            }
            case EXT -> builder.with(remote("203.0.113.9")).header("X-Forwarded-For", "10.0.5.7")
                    .header("X-Real-IP", "10.0.5.7");
            default -> builder.with(user("user-" + who.label.toLowerCase()).authorities(() -> who.label));
        }
        if (withCsrf) builder.with(csrf());
        try {
            return Integer.toString(mvc.perform(builder).andReturn().getResponse().getStatus());
        } catch (Exception e) {
            return "ERR";
        }
    }

    /** Адрес соединения запроса (в MockMvc по умолчанию 127.0.0.1) */
    static RequestPostProcessor remote(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    /** Вся таблица */
    static Map<String, String> table(MockMvc mvc) {
        Map<String, String> table = new LinkedHashMap<>();
        for (String request : requests()) table.put(request, row(mvc, request));
        return table;
    }

    /** Эталон: «МЕТОД /путь» → строка таблицы */
    static Map<String, String> golden() throws IOException {
        Map<String, String> golden = new LinkedHashMap<>();
        for (String line : Files.readAllLines(GOLDEN, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] parts = line.split(" ", 3);
            golden.put(parts[0] + " " + parts[1], line);
        }
        return golden;
    }

    static void record(Map<String, String> table) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# Ответы защиты при тестовом потолке full. Первый эталон снят с SecurityConfig main 7a1e0f0 (0.4.4) до слоя потолка.");
        lines.add("# Столбцы: anon — аноним; ext — аноним с адреса 203.0.113.9 с подложными X-Forwarded-For и X-Real-IP;");
        lines.add("# OPERATOR, DIRECTOR, ADMIN, SECURITY — вошедший с ролью (SECURITY — только чтение).");
        lines.add("# У изменяющих методов: без CSRF-токена|с токеном. 200 — прошёл защиту (заглушка SecurityProbe),");
        lines.add("# 403/401 — отказ защиты, 404 — ворота функции, 500 — /error без ошибки, ERR — исключение в цепочке");
        lines.add("# фильтров (в службе — 500). Пересоздание — AccessMatrix.");
        lines.addAll(table.values());
        Files.createDirectories(GOLDEN.getParent());
        Files.write(GOLDEN, String.join("\r\n", lines).concat("\r\n").getBytes(StandardCharsets.UTF_8));
    }
}

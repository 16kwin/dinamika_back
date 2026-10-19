package com.example.dinamika_back.features;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsProcessor;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Ворота функций (раздел 6.3 проекта): сервлет-фильтр раньше Spring Security. Регистрируется через
 * {@link FeatureWebConfig}, без {@code @Component}.
 *
 * <ol>
 *   <li>Путь под {@code /api/} или {@code /ws-stations} (сырой, декодированный или уже нормализованный контейнером:
 *       {@code /x/../api/…}), а в сыром URI есть {@code ;}, {@code //}, {@code /./}, {@code /../}, {@code %2e},
 *       {@code %2f} или {@code %5c} → 400. Такие запросы отклоняет и StrictHttpFirewall, но ворота держат этот рубеж
 *       сами и не дают им дойти до сверки правил.</li>
 *   <li>Путь без контекста приложения сверяется с правилами выключенных функций: совпадение → 404 со стандартным
 *       телом, одинаково анониму и администратору, на любой метод правила (GET закрывает и HEAD). Ворота отвечают
 *       раньше CorsFilter из Spring Security, поэтому заголовки CORS к своему 404 ставят сами по бину
 *       {@code corsConfigurationSource}: иначе фронт с другого адреса увидел бы ошибку CORS, а не 404 закрытой
 *       функции. Предварительный запрос CORS (OPTIONS) на закрытый путь получает тот же ответ, что CorsFilter дал бы
 *       на любой путь: 200 с заголовками или 403 чужому адресу. До обработчиков он не доходит.</li>
 *   <li>Пропущенным ответам {@code /api/**} ставится заголовок {@code X-AWMS-Features} с ревизией профиля:
 *       по нему фронт узнаёт о перезапуске с другим потолком или о переключении.</li>
 * </ol>
 *
 * <p>Служебные пути (вход, CSRF, ошибки, сами профили) не закрываются никогда. Отказы считаются в памяти по паре
 * (функция, адрес клиента); WARN в лог — не чаще раза в 10 минут на пару, управляющие символы пути в нём
 * экранируются (подделать строку лога нельзя). Сводку в журнал раз в час пишет B6 через {@link #drainDenied()}.
 */
public class FeatureGateFilter extends OncePerRequestFilter {

    public static final String REVISION_HEADER = "X-AWMS-Features";

    /** Не закрываются никогда: это проверяет и контрактный тест реестра */
    static final List<String> ALWAYS_OPEN = List.of("/api/auth/*", "/csrf", "/error", "/logout",
            "/api/features", "/api/features/**", "/api/security-profile", "/api/security-profile/**",
            "/api/admin/features", "/api/admin/features/**");

    private static final Pattern SUSPICIOUS = Pattern.compile(";|//|/\\./|/\\.\\./|/\\.{1,2}$|\\\\|%2e|%2f|%5c",
            Pattern.CASE_INSENSITIVE);
    private static final long WARN_INTERVAL_MILLIS = 10 * 60 * 1000L;
    /** Предел пар (функция, адрес) в памяти: перебор адресов не раздует счётчики */
    private static final int MAX_PAIRS = 10_000;
    /** Предел длины пути в строке лога */
    private static final int LOG_PATH_MAX = 300;
    private static final Logger log = LoggerFactory.getLogger(FeatureGateFilter.class);

    private final FeatureService features;
    private final Supplier<CorsConfigurationSource> cors;
    private final CorsProcessor corsProcessor = new DefaultCorsProcessor();
    private final Clock clock;
    private final UrlPathHelper paths = new UrlPathHelper();
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final Map<String, Denied> denied = new ConcurrentHashMap<>();
    private final AtomicLong overflow = new AtomicLong();

    /** Без CORS: ответы ворот без заголовков CORS (тесты, приложение без бина corsConfigurationSource) */
    public FeatureGateFilter(FeatureService features) {
        this(features, () -> null, Clock.systemUTC());
    }

    /** {@code cors} — настройки CORS приложения; запрашиваются только при отказе, null — без заголовков CORS */
    public FeatureGateFilter(FeatureService features, Supplier<CorsConfigurationSource> cors) {
        this(features, cors, Clock.systemUTC());
    }

    FeatureGateFilter(FeatureService features, Clock clock) {
        this(features, () -> null, clock);
    }

    FeatureGateFilter(FeatureService features, Supplier<CorsConfigurationSource> cors, Clock clock) {
        this.features = features;
        this.cors = cors;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String raw = request.getRequestURI() == null ? "" : request.getRequestURI();
        String path;
        try {
            path = paths.getPathWithinApplication(request);
        } catch (IllegalArgumentException e) {
            // Некорректная %-последовательность: такой путь не сопоставить с правилами
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if ((guarded(raw) || guarded(path) || guarded(containerPath(request))) && SUSPICIOUS.matcher(raw).find()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        FeatureSnapshot snapshot = features.snapshot();
        if (!alwaysOpen(path)) {
            FeatureSnapshot.ClosedRule closed = snapshot.closedRuleFor(request.getMethod(), path,
                    request.getContentType());
            if (closed != null) {
                CorsConfigurationSource source = cors.get();
                if (source != null) {
                    // Как CorsFilter: чужому адресу 403 уже записан, предварительному запросу хватает заголовков (200)
                    if (!corsProcessor.processRequest(source.getCorsConfiguration(request), request, response)) return;
                    if (CorsUtils.isPreFlightRequest(request)) return;
                }
                countDenied(closed, request, path);
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        }
        if (path.startsWith("/api/")) {
            response.setHeader(REVISION_HEADER, snapshot.revision());
        }
        chain.doFilter(request, response);
    }

    private static boolean guarded(String path) {
        return path.startsWith("/api/") || path.equals("/api") || path.startsWith("/ws-stations");
    }

    /** Путь, который контейнер передаст сервлету: уже декодирован и нормализован ({@code /x/../api} → {@code /api}) */
    private static String containerPath(HttpServletRequest request) {
        String servletPath = request.getServletPath() == null ? "" : request.getServletPath();
        return request.getPathInfo() == null ? servletPath : servletPath + request.getPathInfo();
    }

    /**
     * Значение для строки лога: управляющие символы (в том числе перевод строки) — в виде %XX, разделители строк
     * Юникода — кодом символа, длина ограничена
     */
    static String forLog(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(Math.min(value.length(), LOG_PATH_MAX) + 8);
        for (int i = 0; i < value.length(); i++) {
            if (out.length() >= LOG_PATH_MAX) {
                out.append('…');
                break;
            }
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7F) out.append(String.format("%%%02X", (int) c));
            else if (c == 0x85 || c == 0x2028 || c == 0x2029) out.append(String.format("\\u%04X", (int) c));
            else out.append(c);
        }
        return out.toString();
    }

    private boolean alwaysOpen(String path) {
        for (String pattern : ALWAYS_OPEN) {
            if (matcher.match(pattern, path)) return true;
        }
        return false;
    }

    private void countDenied(FeatureSnapshot.ClosedRule closed, HttpServletRequest request, String path) {
        String address = request.getRemoteAddr();
        String key = closed.feature().key() + " " + address;
        Denied entry = denied.get(key);
        if (entry == null) {
            if (denied.size() >= MAX_PAIRS) {
                overflow.incrementAndGet();
                return;
            }
            entry = denied.computeIfAbsent(key, k -> new Denied());
        }
        long count = entry.count.incrementAndGet();
        long now = clock.millis();
        boolean warn;
        synchronized (entry) {
            warn = entry.lastWarn == 0 || now - entry.lastWarn >= WARN_INTERVAL_MILLIS;
            if (warn) entry.lastWarn = now;
        }
        if (warn) {
            log.warn("Ворота функций: 404 на {} {} с адреса {} — функция {} выключена (правило {}), отказов с этого "
                    + "адреса: {}", forLog(request.getMethod()), forLog(path), address, closed.feature().key(),
                    closed.rule().spec(), count);
        }
    }

    /**
     * Сводка отказов с момента прошлого вызова: «функция адрес» → число отказов (ключ «*» — отказы сверх предела
     * пар). Для записи {@code DENIED_SUMMARY} в журнал профиля (B6).
     */
    public Map<String, Long> drainDenied() {
        Map<String, Long> result = new LinkedHashMap<>();
        denied.forEach((key, entry) -> {
            long n = entry.count.getAndSet(0);
            if (n > 0) result.put(key, n);
        });
        denied.entrySet().removeIf(e -> e.getValue().count.get() == 0);
        long lost = overflow.getAndSet(0);
        if (lost > 0) result.put("*", lost);
        return result;
    }

    private static final class Denied {
        final AtomicLong count = new AtomicLong();
        long lastWarn;
    }
}

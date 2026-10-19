package com.example.dinamika_back.config;

import com.example.dinamika_back.features.ConditionalOnFeature;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.GateRule;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ключ колбэков внешней системы заказов (функция {@code orders}, раздел 6.5 проекта). Колбэки SaaS (через Задел)
 * приходят без входа: трек, статус и причина статуса заказа, новое ТКП и статус счёта. Без верного заголовка
 * {@value #HEADER} они получают 401 без подробностей, до контроллера не доходят.
 *
 * <ul>
 *   <li>Пути — только из реестра {@code Feature.ORDERS.callbacks()} (тот же список читает SecurityConfig для
 *       permitAll и CSRF-исключений); своего списка здесь нет. Остальные пути заказов вызывает фронтенд после входа,
 *       фильтр их пропускает.</li>
 *   <li>Ключ — {@code api.key.awms} (после пакета B10 — из файла секретов). Сравнение за постоянное время:
 *       SHA-256 присланного и ожидаемого ключа через {@link MessageDigest#isEqual} — ни раннего выхода, ни утечки
 *       длины. Сам ключ в памяти фильтра не хранится и в лог не пишется.</li>
 *   <li>Ключ не задан — все колбэки отклоняются, при старте WARN.</li>
 *   <li>Порядок — по умолчанию, то есть после springSecurityFilterChain: StrictHttpFirewall к этому моменту уже
 *       отклонил {@code ;}, {@code //} и {@code %2F}, путь сверяется с реестром тем же способом, что в воротах.</li>
 *   <li>Сам класс — не бин: его регистрирует {@link Registration} под {@code @ConditionalOnFeature(ORDERS)}, поэтому
 *       без функции в потолке фильтра нет (и Spring Boot не регистрирует его второй раз как бин-фильтр).</li>
 * </ul>
 */
public class OrdersCallbackKeyFilter extends OncePerRequestFilter {

    /** Заголовок с ключом внешней системы */
    public static final String HEADER = "X-API-Key";

    private static final Logger SECURITY = LoggerFactory.getLogger("AWMS.SECURITY");
    private static final long WARN_INTERVAL_MILLIS = 60_000L;
    private static final int LOG_PATH_MAX = 200;

    /** SHA-256 ожидаемого ключа или null — ключ не задан */
    private final byte[] expected;
    private final Clock clock;
    private final UrlPathHelper paths = new UrlPathHelper();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong lastWarn = new AtomicLong();

    public OrdersCallbackKeyFilter(String apiKey) {
        this(apiKey, Clock.systemUTC());
    }

    OrdersCallbackKeyFilter(String apiKey, Clock clock) {
        this.expected = apiKey == null || apiKey.isBlank() ? null : sha256(apiKey);
        this.clock = clock;
    }

    /** Есть ли ожидаемый ключ (иначе все колбэки отклоняются) */
    public boolean hasKey() {
        return expected != null;
    }

    /** Колбэк ли это внешней системы: сверка с {@code Feature.ORDERS.callbacks()}, как в воротах функций */
    public static boolean isCallback(String method, String path) {
        for (GateRule rule : Feature.ORDERS.callbacks()) {
            if (rule.matches(method, path, null)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path;
        try {
            path = paths.getPathWithinApplication(request);
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (!isCallback(request.getMethod(), path)) {
            chain.doFilter(request, response);
            return;
        }
        String presented = request.getHeader(HEADER);
        if (expected == null || presented == null || !MessageDigest.isEqual(sha256(presented), expected)) {
            reject(request, path, presented == null ? "нет заголовка " + HEADER
                    : expected == null ? "ключ api.key.awms не задан" : "неверный ключ");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }

    /** WARN не чаще раза в минуту с числом отказов; ключ в лог не пишется, путь — с экранированием */
    private void reject(HttpServletRequest request, String path, String reason) {
        long count = rejected.incrementAndGet();
        long now = clock.millis();
        long last = lastWarn.get();
        if ((last == 0 || now - last >= WARN_INTERVAL_MILLIS) && lastWarn.compareAndSet(last, now)) {
            SECURITY.warn("Колбэк заказов отклонён (401): {} {} с адреса {} — {}; отказов с запуска: {}",
                    forLog(request.getMethod()), forLog(path), request.getRemoteAddr(), reason, count);
        }
    }

    /** Число отклонённых колбэков с запуска */
    long rejected() {
        return rejected.get();
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }

    private static String forLog(String value) {
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

    /**
     * Регистрация фильтра (только при функции orders): порядок по умолчанию — после Spring Security. Проверка ключа
     * включается явно — awms.orders.callback-key-required=true в application.properties: до выключателей колбэки
     * принимались без ключа, и внешняя система может его не присылать
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnFeature(Feature.ORDERS)
    @ConditionalOnProperty(name = "awms.orders.callback-key-required", havingValue = "true")
    public static class Registration {

        @Bean
        public FilterRegistrationBean<OrdersCallbackKeyFilter> ordersCallbackKeyFilter(
                @Value("${api.key.awms:}") String apiKey) {
            OrdersCallbackKeyFilter filter = new OrdersCallbackKeyFilter(apiKey);
            if (!filter.hasKey()) {
                SECURITY.warn("Ключ внешней системы заказов api.key.awms не задан: все колбэки SaaS отклоняются (401)");
            }
            FilterRegistrationBean<OrdersCallbackKeyFilter> registration = new FilterRegistrationBean<>(filter);
            registration.setName("ordersCallbackKeyFilter");
            registration.setDispatcherTypes(DispatcherType.REQUEST);
            // Все адреса: какие из них колбэки, решает только реестр (isCallback), второго списка префиксов нет
            registration.addUrlPatterns("/*");
            return registration;
        }
    }
}

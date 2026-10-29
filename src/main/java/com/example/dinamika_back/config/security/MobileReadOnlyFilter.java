package com.example.dinamika_back.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * С телефона, ТСД и планшета — только просмотр (решение команды): вошедший пользователь с такого устройства
 * ({@link ClientDevice}) не меняет данные. Методы, кроме GET, HEAD и OPTIONS, ему разрешены только на адресах
 * {@link PublicEndpoints#viewOnlyWritable()}: вход и выход, выгрузки и печать (чтение), чтение методом POST,
 * личные настройки вида (столбцы, фильтры, сортировка) и транспорты живых обновлений. Остальное — 403 с
 * заголовком {@value #READ_ONLY_HEADER}: фронтенд по нему не обновляет токен, а показывает «только просмотр».
 *
 * <p>Анонимные запросы не затрагивает: станции-шкафы, табло и внешние системы работают как раньше (их ограничивают
 * {@link CeilingAccessFilter} и ключ колбэков). Включено по умолчанию, выключается строкой потолка
 * {@code awms.mobile.read-only=false}. Стоит в цепочке после {@link SecurityRoleReadOnlyFilter}.
 */
public class MobileReadOnlyFilter extends OncePerRequestFilter {

    /** Заголовок отказа: запись с этого устройства запрещена (значение {@code mobile}) */
    public static final String READ_ONLY_HEADER = "X-AWMS-Read-Only";

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("AWMS.SECURITY");
    private static final byte[] BODY = "{\"message\":\"С телефона и планшета доступен только просмотр. Изменить данные можно с компьютера.\"}"
            .getBytes(StandardCharsets.UTF_8);

    private final boolean enabled;
    private final RequestMatcher writable = new OrRequestMatcher(
            PublicEndpoints.viewOnlyWritable().stream().map(PublicEndpoints.Endpoint::matcher).toList());

    public MobileReadOnlyFilter(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (enabled && !isRead(request) && signedIn() && ClientDevice.isMobile(request) && !writable.matches(request)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            SECURITY_LOG.info("Только просмотр с телефона: отклонён {} {} пользователя {}",
                    SecurityRoleReadOnlyFilter.forLog(request.getMethod()),
                    SecurityRoleReadOnlyFilter.forLog(request.getRequestURI()),
                    SecurityRoleReadOnlyFilter.forLog(authentication.getName()));
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setHeader(READ_ONLY_HEADER, ClientDevice.MOBILE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getOutputStream().write(BODY);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isRead(HttpServletRequest request) {
        String method = request.getMethod();
        return method != null && READ_METHODS.contains(method.toUpperCase(Locale.ROOT));
    }

    private static boolean signedIn() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}

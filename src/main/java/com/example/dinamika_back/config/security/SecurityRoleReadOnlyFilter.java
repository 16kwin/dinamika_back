package com.example.dinamika_back.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Роль SECURITY (администратор ИБ) только читает: обязанности разделены, она не правит номенклатуру, станции,
 * справочники и документы. Методы, кроме GET, HEAD и OPTIONS, ей разрешены только на адресах
 * {@link PublicEndpoints#securityWritable()}: профиль безопасности, переключатели (этап 2), вход и выход,
 * выгрузки (чтение: сервер рендерит присланную таблицу), чтение методом POST, личные настройки вида (столбцы,
 * фильтры, сортировка) и SockJS-транспорты живых обновлений.
 *
 * <p>Стоит в цепочке Spring Security после AuthorizationFilter: CSRF и правила доступа уже проверены, отказ —
 * {@link AccessDeniedException}, его превращает в 403 ExceptionTranslationFilter, как любой другой отказ.
 * Роль — через {@link AppRoles}, до пакета B10 — из токена, после — из БД.
 */
public class SecurityRoleReadOnlyFilter extends OncePerRequestFilter {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final int LOG_PATH_MAX = 200;
    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("AWMS.SECURITY");

    private final RequestMatcher writable = new OrRequestMatcher(
            PublicEndpoints.securityWritable().stream().map(PublicEndpoints.Endpoint::matcher).toList());

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase(Locale.ROOT);
        if (!READ_METHODS.contains(method)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (AppRoles.isSecurity(authentication) && !writable.matches(request)) {
                SECURITY_LOG.warn("Роль SECURITY только читает: отклонён {} {} пользователя {}", forLog(method),
                        forLog(request.getRequestURI()), forLog(authentication.getName()));
                throw new AccessDeniedException("Роль SECURITY вне профиля безопасности только читает");
            }
        }
        chain.doFilter(request, response);
    }

    /** Без управляющих символов и не длиннее предела: подделать строку лога нельзя */
    private static String forLog(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(Math.min(value.length(), LOG_PATH_MAX) + 1);
        for (int i = 0; i < value.length(); i++) {
            if (out.length() >= LOG_PATH_MAX) {
                out.append('…');
                break;
            }
            char c = value.charAt(i);
            out.append(c < 0x20 || c == 0x7F || c == 0x85 || c == 0x2028 || c == 0x2029 ? '?' : c);
        }
        return out.toString();
    }
}

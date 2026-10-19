package com.example.dinamika_back.config.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Проверка ролей в коде контроллеров и сервисов.
 *
 * <p>Роль в базе записана без префикса (ADMIN), а в токенах может встречаться и ROLE_ADMIN —
 * здесь понимаются оба вида. Правила {@code hasRole(...)} в SecurityConfig ждут ROLE_-префикс,
 * поэтому новые проверки ролей идут через этот класс.
 */
public final class AppRoles {

    /** Администратор ИБ: вне страницы «Безопасность» только читает ({@link SecurityRoleReadOnlyFilter}) */
    public static final String SECURITY = "SECURITY";

    /** Роли руководителя: видео станций, настройки камер, редактирование карты станций */
    public static final Set<String> MANAGERS = Set.of("ADMIN", "DIRECTOR");

    /** Видят страницу «Безопасность» и отчёт «Профиль безопасности» (SECURITY — администратор ИБ) */
    public static final Set<String> SECURITY_VIEWERS = Set.of("ADMIN", "DIRECTOR", SECURITY);

    /** Кто по умолчанию выключает функции переключателями (этап 2, awms.features.admin.disable-roles) */
    public static final Set<String> FEATURE_DISABLERS_DEFAULT = Set.of("ADMIN", SECURITY);

    /** Кто включает функции обратно в режиме disable-only (этап 2) */
    public static final Set<String> FEATURE_ENABLERS = Set.of(SECURITY);

    /**
     * Роли, которые знает код, с названиями для матрицы ролей: ADMIN и OPERATOR — V1, DIRECTOR — V14,
     * SECURITY — пользователей заводит команда create-user (B10). Порядок — как в отчёте
     */
    public static final Map<String, String> KNOWN = known();

    private AppRoles() {
    }

    private static Map<String, String> known() {
        Map<String, String> roles = new LinkedHashMap<>();
        roles.put("ADMIN", "Администратор системы");
        roles.put("DIRECTOR", "Директор");
        roles.put("OPERATOR", "Оператор");
        roles.put(SECURITY, "Администратор ИБ");
        return Collections.unmodifiableMap(roles);
    }

    /**
     * Роли из строки потолка {@code "ADMIN, director"} → {ADMIN, DIRECTOR}: без префикса ROLE_, в верхнем регистре,
     * по алфавиту; пустая строка — пустой набор
     */
    public static Set<String> parse(String csv) {
        Set<String> roles = new TreeSet<>();
        if (csv != null) {
            for (String part : csv.split(",")) {
                String role = roleName(part);
                if (!role.isEmpty()) roles.add(role);
            }
        }
        return Collections.unmodifiableSet(roles);
    }

    /** ROLE_ADMIN → ADMIN */
    public static String roleName(String authority) {
        if (authority == null) return "";
        String role = authority.trim().toUpperCase();
        return role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role;
    }

    /**
     * Есть ли у вошедшего пользователя одна из ролей. Аноним не подходит ни под одну роль: у его токена
     * isAuthenticated() = true и полномочие ROLE_ANONYMOUS, и строка {@code roles=ANONYMOUS} в потолке
     * иначе открыла бы функцию без входа
     */
    public static boolean hasAny(Authentication authentication, Set<String> roles) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) return false;
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(AppRoles::roleName)
                .anyMatch(roles::contains);
    }

    public static boolean isManager(Authentication authentication) {
        return hasAny(authentication, MANAGERS);
    }

    /** Администратор ИБ: вне профиля безопасности и переключателей только читает */
    public static boolean isSecurity(Authentication authentication) {
        return hasAny(authentication, Set.of(SECURITY));
    }

    /** Текущий пользователь — администратор или директор */
    public static boolean currentIsManager() {
        return isManager(SecurityContextHolder.getContext().getAuthentication());
    }
}

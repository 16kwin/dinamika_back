package com.example.dinamika_back.service;

import com.example.dinamika_back.config.security.AppRoles;
import org.springframework.security.core.Authentication;

import java.util.Set;

/**
 * Права на видео по ролям.
 * <ul>
 *   <li>Администратор и директор — всё: живой просмотр станций, настройки камер, журнал видеоархива.</li>
 *   <li>Остальные роли — только журнал видеоархива: список записей и просмотр сохранённого видео.</li>
 * </ul>
 * Названия ролей разбирает {@link AppRoles}: понимаются и ADMIN, и ROLE_ADMIN.
 */
public final class VideoAccess {

    /** Роли с полным доступом к видео */
    public static final Set<String> MANAGER_ROLES = AppRoles.MANAGERS;

    private VideoAccess() {
    }

    public static boolean canManage(Authentication authentication) {
        return AppRoles.isManager(authentication);
    }

    /** 403, если у текущего пользователя нет полного доступа к видео */
    public static void requireManager() {
        if (!AppRoles.currentIsManager()) {
            throw VideoException.forbidden("Живой просмотр и настройки камер доступны администратору и директору");
        }
    }

    /** ROLE_ADMIN → ADMIN */
    static String roleName(String authority) {
        return AppRoles.roleName(authority);
    }
}

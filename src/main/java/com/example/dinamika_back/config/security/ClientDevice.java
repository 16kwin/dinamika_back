package com.example.dinamika_back.config.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.regex.Pattern;

/**
 * Запрос с телефона, ТСД или планшета (не с компьютера) — для режима «только просмотр» ({@link MobileReadOnlyFilter}).
 *
 * <p>Признаки, любой из них:
 * <ul>
 *   <li>{@code X-AWMS-Device: mobile} — ставит фронтенд, если экран сенсорный без мыши (сюда попадает и «версия для
 *       ПК» в мобильном браузере, и iPad, который называет себя Macintosh);</li>
 *   <li>{@code Sec-CH-UA-Mobile: ?1} — признак мобильного браузера Chromium (Chrome, Яндекс Браузер, Edge);</li>
 *   <li>User-Agent мобильной системы: Android, iPhone, iPad, iPod, Windows Phone, «Mobile».</li>
 * </ul>
 * Заголовки подделать можно — это правило работы, а не граница безопасности: пароль у пользователя есть и так.
 * Компьютер с сенсорным экраном и мышью считается компьютером.
 */
public final class ClientDevice {

    /** Заголовок фронтенда: устройство без мыши (телефон, ТСД, планшет) */
    public static final String DEVICE_HEADER = "X-AWMS-Device";
    public static final String MOBILE = "mobile";

    private static final Pattern MOBILE_USER_AGENT =
            Pattern.compile("Android|iPhone|iPad|iPod|Windows Phone|IEMobile|Opera Mini|Mobile", Pattern.CASE_INSENSITIVE);

    private ClientDevice() {
    }

    public static boolean isMobile(HttpServletRequest request) {
        if (MOBILE.equalsIgnoreCase(trim(request.getHeader(DEVICE_HEADER)))) return true;
        if ("?1".equals(trim(request.getHeader("Sec-CH-UA-Mobile")))) return true;
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null && MOBILE_USER_AGENT.matcher(userAgent).find();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}

package com.example.dinamika_back.config.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Только просмотр с телефона и планшета: вошедший пользователь с мобильного устройства не меняет данные, но входит,
 * выходит, читает методом POST, выгружает и сохраняет личные настройки вида. Компьютер и анонимные устройства цеха
 * (станции, табло) — как раньше.
 */
class MobileReadOnlyFilterTest {

    private static final String ANDROID = "Mozilla/5.0 (Linux; Android 14; SM-A546B) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/130.0 YaBrowser/24.10 Mobile Safari/537.36";
    private static final String IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1";
    private static final String WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/130.0 Safari/537.36";

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "director_test", null, AuthorityUtils.createAuthorityList("ROLE_DIRECTOR")));
    }

    private static MockHttpServletRequest request(String method, String uri, String userAgent) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setServletPath(uri);
        if (userAgent != null) request.addHeader("User-Agent", userAgent);
        return request;
    }

    private static MockHttpServletResponse run(MobileReadOnlyFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void phoneCannotChangeData() throws Exception {
        signIn();
        MobileReadOnlyFilter filter = new MobileReadOnlyFilter(true);
        for (String[] call : new String[][] {
                {"POST", "/api/move-orders"}, {"PATCH", "/api/nomenclature/items/1"}, {"DELETE", "/api/employees/5"},
                {"PUT", "/api/gpp"}, {"POST", "/api/nomenclature/0a/ratings"}}) {
            MockHttpServletResponse response = run(filter, request(call[0], call[1], ANDROID));
            assertThat(response.getStatus()).as(call[0] + " " + call[1]).isEqualTo(403);
            assertThat(response.getHeader(MobileReadOnlyFilter.READ_ONLY_HEADER)).isEqualTo("mobile");
            assertThat(response.getContentAsString()).contains("только просмотр");
        }
    }

    @Test
    void phoneStillReadsSignsInAndKeepsViewSettings() throws Exception {
        signIn();
        MobileReadOnlyFilter filter = new MobileReadOnlyFilter(true);
        for (String[] call : new String[][] {
                {"GET", "/api/move-orders"}, {"HEAD", "/api/nomenclature/items"},
                {"POST", "/api/auth/login"}, {"POST", "/logout"},
                {"POST", "/api/stations/static/filtered"}, {"POST", "/api/nomenclature/calculate-compatibility"},
                {"PATCH", "/api/employees/columns-settings"}, {"PATCH", "/api/nomenclature/settings"},
                {"POST", "/api/user/filters"}, {"POST", "/api/security-profile/events"}}) {
            assertThat(run(filter, request(call[0], call[1], IPHONE)).getStatus()).as(call[0] + " " + call[1])
                    .isEqualTo(200);
        }
    }

    @Test
    void phoneCannotManageInstallation() throws Exception {
        signIn();
        MobileReadOnlyFilter filter = new MobileReadOnlyFilter(true);
        assertThat(run(filter, request("POST", "/api/security-profile/purge", ANDROID)).getStatus()).isEqualTo(403);
    }

    @Test
    void computerAndAnonymousDevicesAreNotAffected() throws Exception {
        MobileReadOnlyFilter filter = new MobileReadOnlyFilter(true);
        // станция-шкаф или табло на Android без входа
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThat(run(filter, request("POST", "/api/orders/1/status", ANDROID)).getStatus()).isEqualTo(200);
        SecurityContextHolder.clearContext();
        assertThat(run(filter, request("POST", "/api/stations", ANDROID)).getStatus()).isEqualTo(200);

        signIn();
        assertThat(run(filter, request("POST", "/api/move-orders", WINDOWS)).getStatus()).isEqualTo(200);
    }

    @Test
    void deviceHeaderAndClientHintCountAsMobile() throws Exception {
        signIn();
        MobileReadOnlyFilter filter = new MobileReadOnlyFilter(true);
        // «Версия для ПК» в мобильном браузере: строка как у компьютера, но фронтенд видит сенсорный экран без мыши
        MockHttpServletRequest desktopMode = request("POST", "/api/move-orders", WINDOWS);
        desktopMode.addHeader(ClientDevice.DEVICE_HEADER, "mobile");
        assertThat(run(filter, desktopMode).getStatus()).isEqualTo(403);

        MockHttpServletRequest hint = request("DELETE", "/api/employees/5", WINDOWS);
        hint.addHeader("Sec-CH-UA-Mobile", "?1");
        assertThat(run(filter, hint).getStatus()).isEqualTo(403);

        MockHttpServletRequest notMobile = request("DELETE", "/api/employees/5", WINDOWS);
        notMobile.addHeader("Sec-CH-UA-Mobile", "?0");
        assertThat(run(filter, notMobile).getStatus()).isEqualTo(200);
    }

    @Test
    void switchedOffByCeiling() throws Exception {
        signIn();
        assertThat(run(new MobileReadOnlyFilter(false), request("POST", "/api/move-orders", ANDROID)).getStatus())
                .isEqualTo(200);
    }
}

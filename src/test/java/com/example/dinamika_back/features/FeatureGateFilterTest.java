package com.example.dinamika_back.features;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ворота функций: для каждого правила каждой функции строится конкретный адрес ({@code *} → 1, {@code **} → x).
 * Функция выключена → 404, включена → запрос проходит; правило GET действует и на HEAD. Загрузки, выгрузки,
 * обходы через кодирование пути и заголовок ревизии.
 */
class FeatureGateFilterTest {

    /** Все функции включены: full + выключенные в нём по проекту служебные */
    private static Map<String, Object> allOn() {
        Map<String, Object> props = new HashMap<>();
        props.put("awms.features.preset", "full");
        for (Feature f : Feature.real()) {
            if (!f.isOnIn(FeaturePreset.FULL)) props.put("awms.features." + f.key() + ".enabled", "true");
        }
        return props;
    }

    private static FeatureService service(Map<String, Object> props) {
        return new FeatureService(FeatureResolver.fromProperties(props));
    }

    private static FeatureService allOnExcept(Feature off) {
        Map<String, Object> props = allOn();
        props.put("awms.features." + off.key() + ".enabled", "false");
        return service(props);
    }

    private static Result call(FeatureService features, String method, String uri, String contentType) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        if (contentType != null) request.setContentType(contentType);
        request.setRemoteAddr("10.0.0.7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new FeatureGateFilter(features).doFilter(request, response, chain);
        return new Result(response.getStatus(), chain.getRequest() != null, response.getHeader(FeatureGateFilter.REVISION_HEADER));
    }

    private record Result(int status, boolean passed, String revision) {
    }

    @Test
    void everyRuleClosesWhenFeatureOffAndPassesWhenOn() throws Exception {
        FeatureService on = service(allOn());
        for (Feature f : Feature.real()) {
            FeatureService off = allOnExcept(f);
            for (GateRule rule : f.rules()) {
                if (rule.kind() == GateRule.Kind.MIXED) continue;
                String contentType = rule.kind() == GateRule.Kind.MULTIPART ? "multipart/form-data; boundary=x" : null;
                List<String> methods = rule.method() == null ? List.of("GET", "POST", "DELETE")
                        : "GET".equals(rule.method()) ? List.of("GET", "HEAD") : List.of(rule.method());
                for (String method : methods) {
                    String what = f.key() + " " + rule + " → " + method + " " + rule.sampleUrl();
                    Result closed = call(off, method, rule.sampleUrl(), contentType);
                    assertThat(closed.status()).as("выключено: " + what).isEqualTo(404);
                    assertThat(closed.passed()).as("выключено: " + what).isFalse();
                    Result open = call(on, method, rule.sampleUrl(), contentType);
                    assertThat(open.passed()).as("включено: " + what).isTrue();
                    assertThat(open.status()).as("включено: " + what).isEqualTo(200);
                }
            }
        }
    }

    @Test
    void doubleStarRuleClosesBasePathToo() throws Exception {
        // /api/gpp/** закрывает и сам /api/gpp (GET и PUT ГПП), /ws-stations/** — и /ws-stations
        for (Feature f : Feature.real()) {
            FeatureService off = allOnExcept(f);
            for (GateRule rule : f.rules()) {
                if (rule.kind() != GateRule.Kind.GATE || !rule.pattern().endsWith("/**")) continue;
                String base = rule.pattern().substring(0, rule.pattern().length() - 3).replace("*", "1");
                String method = rule.method() == null ? "PUT" : rule.method();
                assertThat(call(off, method, base, null).status()).as(f.key() + " " + rule + " → " + base).isEqualTo(404);
            }
        }
    }

    @Test
    void parentRuleClosesChildrenAndOverlappingRulesCloseTogether() throws Exception {
        FeatureService videoOff = allOnExcept(Feature.VIDEO);
        assertThat(call(videoOff, "GET", "/api/video/status", null).status()).isEqualTo(404);
        assertThat(call(videoOff, "HEAD", "/api/video/cameras/1/live", null).status()).isEqualTo(404);
        // Скачивание клипа закрыто и при выключенном video.clips, и при выключенном video.download
        assertThat(call(allOnExcept(Feature.VIDEO_CLIPS), "GET", "/api/video/clips/1/download", null).status()).isEqualTo(404);
        assertThat(call(allOnExcept(Feature.VIDEO_DOWNLOAD), "GET", "/api/video/clips/1/download", null).status()).isEqualTo(404);
        // Выключен только просмотр архива: живой поток работает
        assertThat(call(allOnExcept(Feature.VIDEO_ARCHIVE), "GET", "/api/video/cameras/1/live", null).passed()).isTrue();
    }

    @Test
    void uploadsCloseOnlyFilePartsBeforeB4b() throws Exception {
        FeatureService off = allOnExcept(Feature.UPLOADS);
        String multipart = "multipart/form-data; boundary=x";
        assertThat(call(off, "POST", "/api/nomenclature/1/images", multipart).status()).isEqualTo(404);
        assertThat(call(off, "POST", "/api/nomenclature/1/images", "application/json").passed()).isTrue();
        // Смешанные пути (файл необязателен): до B4b ворота пропускают и JSON, и multipart — файл отклоняет сторож
        for (String path : List.of("/api/nomenclature/1/codes", "/api/nomenclature/1/supply", "/api/suppliers/1/deliveries")) {
            assertThat(call(off, "POST", path, multipart).passed()).as(path).isTrue();
            assertThat(call(off, "POST", path, "application/json").passed()).as(path).isTrue();
        }
        // Просмотр загруженного остаётся
        assertThat(call(off, "GET", "/api/nomenclature/1/images", null).passed()).isTrue();
        assertThat(call(off, "GET", "/uploads/a.png", null).passed()).isTrue();
    }

    @Test
    void exportSuffixesAreClosedOnAnyPrefix() throws Exception {
        FeatureService off = allOnExcept(Feature.EXPORT);
        for (String path : List.of("/api/templates/export-pdf", "/api/stations/crud/export-excel",
                "/api/station-types/export-word", "/api/sections/crud/print")) {
            assertThat(call(off, "POST", path, "application/json").status()).as(path).isEqualTo(404);
        }
        assertThat(call(off, "POST", "/api/templates/print-settings", "application/json").passed()).isTrue();
    }

    @Test
    void encodedAndDotSegmentBypassesAreRejected() throws Exception {
        FeatureService on = service(allOn());
        for (String uri : List.of("//api/video/status", "/api//video/status", "/api/video;a=b/status",
                "/api/video%2Fstatus", "/api/%2e%2e/video/status", "/api/video/../video/status",
                "/api/video/./status", "/api/video%5cstatus", "/ws-stations;x/info", "/api/video/status/..")) {
            Result r = call(on, "GET", uri, null);
            assertThat(r.status()).as(uri).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(r.passed()).as(uri).isFalse();
        }
        // Вне /api/ и /ws-stations ворота строгую проверку не делают (её делает StrictHttpFirewall)
        assertThat(call(on, "GET", "/uploads/a;b.png", null).passed()).isTrue();
    }

    @Test
    void dotSegmentsBeforeApiAreRejectedByContainerPath() throws Exception {
        // Tomcat нормализует /x/../api/video/status в /api/video/status: сырой путь с /api не начинается
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x/../api/video/status");
        request.setServletPath("/api/video/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new FeatureGateFilter(service(allOn())).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void logValuesHaveNoLineBreaks() {
        assertThat(FeatureGateFilter.forLog("/api/video/a\nfake-line\r\t\u2028x"))
                .isEqualTo("/api/video/a%0Afake-line%0D%09\\u2028x");
        assertThat(FeatureGateFilter.forLog("/api/" + "a".repeat(1000))).hasSize(301).endsWith("…");
        assertThat(FeatureGateFilter.forLog("/api/видео/1")).isEqualTo("/api/видео/1");
    }

    @Test
    void gateResponsesCarryCorsHeadersForAllowedOrigin() throws Exception {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        FeatureGateFilter filter = new FeatureGateFilter(allOnExcept(Feature.VIDEO), () -> source,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        // Запрос фронта с другого адреса: 404 закрытой функции с заголовками CORS — браузер покажет его фронту
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/video/status");
        get.addHeader("Origin", "http://localhost:3000");
        MockHttpServletResponse closed = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(get, closed, chain);
        assertThat(closed.getStatus()).isEqualTo(404);
        assertThat(chain.getRequest()).isNull();
        assertThat(closed.getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:3000");
        assertThat(closed.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");

        // Предварительный запрос: 200 с заголовками, как ответил бы CorsFilter; до обработчиков не доходит
        MockHttpServletRequest preflight = new MockHttpServletRequest("OPTIONS", "/api/video/status");
        preflight.addHeader("Origin", "http://localhost:3000");
        preflight.addHeader("Access-Control-Request-Method", "GET");
        MockHttpServletResponse ok = new MockHttpServletResponse();
        MockFilterChain preflightChain = new MockFilterChain();
        filter.doFilter(preflight, ok, preflightChain);
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(preflightChain.getRequest()).isNull();
        assertThat(ok.getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:3000");

        // Чужой адрес: 403, как от CorsFilter на любом пути
        MockHttpServletRequest foreign = new MockHttpServletRequest("GET", "/api/video/status");
        foreign.addHeader("Origin", "http://evil.example");
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(foreign, rejected, new MockFilterChain());
        assertThat(rejected.getStatus()).isEqualTo(403);
        assertThat(rejected.getHeader("Access-Control-Allow-Origin")).isNull();

        // Тот же адрес (не CORS): обычный 404. В счётчик отказов попали два запроса, не предварительный и не чужой
        MockHttpServletResponse plain = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/video/status"), plain, new MockFilterChain());
        assertThat(plain.getStatus()).isEqualTo(404);
        assertThat(plain.getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(filter.drainDenied()).containsExactly(Map.entry("video 127.0.0.1", 2L));
    }

    @Test
    void serviceEndpointsStayOpenInIsolatedProfile() throws Exception {
        FeatureService isolated = service(Map.of("awms.features.preset", "isolated"));
        for (String path : List.of("/api/features", "/api/features/public", "/api/auth/login", "/csrf", "/error")) {
            assertThat(call(isolated, "GET", path, null).passed()).as(path).isTrue();
        }
        assertThat(call(isolated, "GET", "/ws-stations/info", null).status()).isEqualTo(404);
        assertThat(call(isolated, "GET", "/api/orders/active", null).status()).isEqualTo(404);
        // Ядро учёта работает
        assertThat(call(isolated, "GET", "/api/nomenclature/tree", null).passed()).isTrue();
        assertThat(call(isolated, "POST", "/api/nomenclature/1/codes", "multipart/form-data; boundary=x").passed()).isTrue();
    }

    @Test
    void revisionHeaderOnApiResponsesChangesWithFingerprint() throws Exception {
        FeatureService first = service(Map.of("awms.features.preset", "standard"));
        FeatureService second = service(Map.of("awms.features.preset", "standard", "awms.video.retention-days", "60"));
        Result a = call(first, "GET", "/api/nomenclature/tree", null);
        Result b = call(second, "GET", "/api/nomenclature/tree", null);
        assertThat(a.revision()).isEqualTo(first.revision()).matches("[0-9a-f]{12}-0");
        assertThat(b.revision()).isNotEqualTo(a.revision());
        // Вне /api/ заголовка нет
        assertThat(call(first, "GET", "/uploads/a.png", null).revision()).isNull();
    }

    @Test
    void deniedRequestsAreCountedPerFeatureAndAddress() throws Exception {
        FeatureService off = allOnExcept(Feature.GPP);
        FeatureGateFilter filter = new FeatureGateFilter(off, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/gpp");
            request.setRemoteAddr("10.0.0.9");
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }
        assertThat(filter.drainDenied()).containsExactly(Map.entry("gpp 10.0.0.9", 3L));
        assertThat(filter.drainDenied()).isEmpty();
    }
}

package com.example.dinamika_back.config.security;

import com.example.dinamika_back.features.FeatureResolver;
import com.example.dinamika_back.features.FeatureSnapshot;
import com.example.dinamika_back.features.journal.FeatureJournal;
import com.example.dinamika_back.features.net.IpNetwork;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Адрес клиента за прокси ({@link ClientAddress}) и атрибут адреса для журналов: заголовки — только от доверенных
 * прокси, X-Forwarded-For справа налево, расхождение заголовков — адрес неизвестен. Набор доверенных прокси задаётся
 * потолком ({@code awms.security.trusted-proxies}), пустой — заголовкам не верить совсем.
 */
class ClientAddressTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static FeatureSnapshot ceiling(String... keyValues) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("awms.features.preset", "full");
        for (int i = 0; i < keyValues.length; i += 2) properties.put(keyValues[i], keyValues[i + 1]);
        return FeatureResolver.fromProperties(properties);
    }

    private static MockHttpServletRequest request(String remote, String... headers) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/stations/static");
        request.setRemoteAddr(remote);
        for (int i = 0; i < headers.length; i += 2) request.addHeader(headers[i], headers[i + 1]);
        return request;
    }

    private static String resolve(ClientAddress resolver, MockHttpServletRequest request) {
        ClientAddress.Resolved r = resolver.resolve(request);
        return r.known() ? r.text() : null;
    }

    @Test
    void defaultTrustsOnlyLocalNginx() {
        ClientAddress resolver = ClientAddress.of(ceiling());
        assertThat(resolver.trustedProxies()).extracting(IpNetwork::toString).containsExactly("127.0.0.1", "::1");
        assertThat(resolve(resolver, request("127.0.0.1", "X-Real-IP", "10.0.5.7"))).isEqualTo("10.0.5.7");
        assertThat(resolve(resolver, request("0:0:0:0:0:0:0:1", "X-Forwarded-For", "10.0.5.7"))).isEqualTo("10.0.5.7");
        // Не от прокси — адрес соединения, заголовки не читаются
        assertThat(resolve(resolver, request("10.0.9.1", "X-Forwarded-For", "10.0.5.7", "X-Real-IP", "10.0.5.7")))
                .isEqualTo("10.0.9.1");
        // От прокси без заголовков — сам прокси
        assertThat(resolve(resolver, request("127.0.0.1"))).isEqualTo("127.0.0.1");
    }

    @Test
    void forwardedForIsReadFromTheRightAndHeadersMustAgree() {
        ClientAddress resolver = ClientAddress.of(ceiling("awms.security.trusted-proxies", "127.0.0.1, 10.9.0.0/30"));
        assertThat(resolve(resolver, request("10.9.0.2", "X-Forwarded-For", "10.0.5.7, 203.0.113.9")))
                .isEqualTo("203.0.113.9");
        assertThat(resolve(resolver, request("10.9.0.2", "X-Forwarded-For", "10.0.5.7, 10.9.0.1")))
                .isEqualTo("10.0.5.7");
        // Несколько строк заголовка — одна цепочка по порядку
        MockHttpServletRequest twoLines = request("10.9.0.2", "X-Forwarded-For", "10.0.5.7");
        twoLines.addHeader("X-Forwarded-For", "10.0.6.1");
        assertThat(resolve(resolver, twoLines)).isEqualTo("10.0.6.1");
        // X-Real-IP внутреннего прокси (доверенный адрес) клиента не называет
        assertThat(resolve(resolver, request("127.0.0.1", "X-Forwarded-For", "10.0.5.7, 10.9.0.1", "X-Real-IP", "10.9.0.1")))
                .isEqualTo("10.0.5.7");
        // Расхождение и мусор — адрес неизвестен
        assertThat(resolve(resolver, request("127.0.0.1", "X-Forwarded-For", "10.0.5.7", "X-Real-IP", "10.0.5.8"))).isNull();
        assertThat(resolve(resolver, request("127.0.0.1", "X-Forwarded-For", "10.0.5.7, unknown"))).isNull();
        assertThat(resolve(resolver, request("127.0.0.1", "X-Real-IP", "station-1"))).isNull();
        // Порт и квадратные скобки снимаются, адрес — в канонической записи
        assertThat(resolve(resolver, request("127.0.0.1", "X-Forwarded-For", "[fd00::1]:443"))).isEqualTo("fd00:0:0:0:0:0:0:1");
        assertThat(resolve(resolver, request("127.0.0.1", "X-Forwarded-For", "10.0.5.7:51234"))).isEqualTo("10.0.5.7");
    }

    @Test
    void emptyTrustedProxiesMeansHeadersAreIgnored() {
        ClientAddress resolver = ClientAddress.of(ceiling("awms.security.trusted-proxies", ""));
        assertThat(resolver.trustedProxies()).isEmpty();
        assertThat(resolve(resolver, request("127.0.0.1", "X-Forwarded-For", "10.0.5.7", "X-Real-IP", "10.0.5.7")))
                .isEqualTo("127.0.0.1");
    }

    @Test
    void filterSetsClientAddressForJournalsAndRejectsOutsiders() throws Exception {
        CeilingAccessFilter filter = new CeilingAccessFilter(ceiling(
                "awms.security.anonymous.allowed-networks", "10.0.5.0/24"));
        assertThat(filter.narrows()).isTrue();
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        MockHttpServletRequest allowed = request("127.0.0.1", "X-Forwarded-For", "10.0.5.7", "X-Real-IP", "10.0.5.7");
        MockHttpServletResponse ok = new MockHttpServletResponse();
        MockFilterChain passed = new MockFilterChain();
        filter.doFilter(allowed, ok, passed);
        assertThat(passed.getRequest()).isNotNull();
        assertThat(allowed.getAttribute(FeatureJournal.CLIENT_IP_ATTRIBUTE)).isEqualTo("10.0.5.7");

        MockHttpServletRequest outsider = request("127.0.0.1", "X-Real-IP", "203.0.113.9");
        MockHttpServletResponse denied = new MockHttpServletResponse();
        MockFilterChain stopped = new MockFilterChain();
        filter.doFilter(outsider, denied, stopped);
        assertThat(denied.getStatus()).isEqualTo(401);
        assertThat(stopped.getRequest()).isNull();
        assertThat(outsider.getAttribute(FeatureJournal.CLIENT_IP_ATTRIBUTE)).isEqualTo("203.0.113.9");

        // Адрес неизвестен — атрибута нет (журнал возьмёт адрес соединения), запрос отклонён
        MockHttpServletRequest forged = request("127.0.0.1", "X-Forwarded-For", "10.0.5.7", "X-Real-IP", "203.0.113.9");
        MockHttpServletResponse forgedResponse = new MockHttpServletResponse();
        filter.doFilter(forged, forgedResponse, new MockFilterChain());
        assertThat(forgedResponse.getStatus()).isEqualTo(401);
        assertThat(forged.getAttribute(FeatureJournal.CLIENT_IP_ATTRIBUTE)).isNull();
    }

    @Test
    void fullWithoutRolesAndNetworksNarrowsNothing() throws Exception {
        CeilingAccessFilter filter = new CeilingAccessFilter(ceiling());
        assertThat(filter.narrows()).isFalse();
        for (String remote : List.of("127.0.0.1", "203.0.113.9")) {
            MockHttpServletRequest request = request(remote, "X-Forwarded-For", "garbage");
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).isNotNull();
        }
    }
}

package com.example.dinamika_back.config.security;

import com.example.dinamika_back.features.FeatureService;
import com.example.dinamika_back.features.FeaturesController;
import com.example.dinamika_back.service.AuthService;
import com.example.dinamika_back.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static com.example.dinamika_back.config.security.AccessMatrix.remote;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Решение пользователя: станции-шкафы и табло ходят в API без входа, а безопасник может разрешить это только
 * с адресов устройств цеха ({@code awms.security.anonymous.allowed-networks}). Анонимный запрос с адреса вне
 * списка — 401; вход, выход, CSRF-токен и флаги до входа — с любого адреса; вошедших список не касается.
 * За nginx адрес клиента берётся из X-Forwarded-For / X-Real-IP только от доверенного прокси (умолчание —
 * 127.0.0.1 и ::1), подделка заголовка проверку не обходит.
 */
@WebMvcTest(controllers = FeaturesController.class)
@Import({SecurityConfig.class, FeatureService.class, SecurityProbe.class})
@TestPropertySource(properties = {
        "awms.features.preset=full",
        "awms.security.anonymous.allowed-networks=10.0.5.0/24, 10.0.6.17, fd00:5::/64"})
class AnonymousNetworksTest {

    private static final List<String> BOARD = List.of("/api/stations/static", "/api/stations/dynamic/1",
            "/api/locations/hierarchy", "/uploads/photo.png", "/ws-stations/info", "/api/nomenclature/tree",
            "/api/dashboard/control-events");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthService authService;
    @MockBean
    private RefreshTokenService refreshTokenService;

    /** Прямое подключение к порту службы с адреса устройства */
    private static MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder request, String address) {
        return request.with(remote(address));
    }

    @Test
    void workshopDevicesKeepAnonymousAccess() throws Exception {
        for (String url : BOARD) {
            mvc.perform(from(get(url), "10.0.5.7")).andExpect(status().isOk());
            mvc.perform(from(get(url), "10.0.6.17")).andExpect(status().isOk());
            mvc.perform(from(get(url), "fd00:5:0:0::20")).andExpect(status().isOk());
            // IPv4 в форме IPv6, как его иногда отдаёт контейнер
            mvc.perform(from(get(url), "::ffff:10.0.5.8")).andExpect(status().isOk());
        }
        mvc.perform(from(post("/api/stations/dynamic/filtered"), "10.0.5.7").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
    }

    @Test
    void otherAddressesNeedSignIn() throws Exception {
        for (String url : BOARD) {
            mvc.perform(from(get(url), "10.0.6.18")).andExpect(status().isUnauthorized());
            mvc.perform(from(get(url), "192.168.1.10")).andExpect(status().isUnauthorized());
            mvc.perform(from(get(url), "fd00:6::20")).andExpect(status().isUnauthorized());
            // Вошедший пользователь — с любого адреса
            mvc.perform(from(get(url), "192.168.1.10").with(user("ivanov").authorities(() -> "OPERATOR")))
                    .andExpect(status().isOk());
        }
        // Колбэки заказов — тоже анонимные запросы: адрес внешней системы вносится в список
        mvc.perform(from(post("/api/orders/1/status"), "192.168.1.10").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(from(post("/api/orders/1/status"), "10.0.5.30").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isOk());
        // Отказы прежних правил не меняются: /api/features анониму — 403 с любого адреса
        mvc.perform(from(get("/api/features"), "10.0.5.7")).andExpect(status().isForbidden());
        mvc.perform(from(get("/api/features"), "192.168.1.10")).andExpect(status().isForbidden());
    }

    @Test
    void serviceAddressesWorkFromAnywhere() throws Exception {
        for (String address : List.of("192.168.1.10", "203.0.113.9")) {
            mvc.perform(from(get("/api/features/public"), address)).andExpect(status().isOk());
            mvc.perform(from(get("/csrf"), address)).andExpect(status().isOk());
            mvc.perform(from(post("/api/auth/login"), address).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk());
            mvc.perform(from(post("/api/auth/refresh_token"), address)).andExpect(status().isOk());
            mvc.perform(from(post("/api/auth/check_password"), address).with(csrf())).andExpect(status().isOk());
            mvc.perform(from(post("/logout"), address).with(csrf())
                    .with(user("ivanov").authorities(() -> "OPERATOR"))).andExpect(status().isOk());
        }
    }

    @Test
    void forwardedHeadersAreTrustedOnlyFromTrustedProxy() throws Exception {
        String url = "/api/stations/static";
        // Клиент подключился напрямую и прислал заголовки с адресом станции — заголовки не читаются
        mvc.perform(from(get(url), "203.0.113.9").header("X-Forwarded-For", "10.0.5.7")).andExpect(status().isUnauthorized());
        mvc.perform(from(get(url), "203.0.113.9").header("X-Real-IP", "10.0.5.7")).andExpect(status().isUnauthorized());
        // Подключился напрямую с адреса станции: заголовки тоже не читаются, адрес соединения в списке
        mvc.perform(from(get(url), "10.0.5.7").header("X-Forwarded-For", "203.0.113.9")).andExpect(status().isOk());

        // Через nginx на этом же сервере (127.0.0.1, ::1): шаблон deploy/nginx ставит оба заголовка = $remote_addr
        mvc.perform(from(get(url), "127.0.0.1").header("X-Forwarded-For", "10.0.5.7").header("X-Real-IP", "10.0.5.7"))
                .andExpect(status().isOk());
        mvc.perform(from(get(url), "0:0:0:0:0:0:0:1").header("X-Real-IP", "10.0.6.17")).andExpect(status().isOk());
        mvc.perform(from(get(url), "127.0.0.1").header("X-Forwarded-For", "203.0.113.9").header("X-Real-IP", "203.0.113.9"))
                .andExpect(status().isUnauthorized());
        // Без заголовков клиент — сам nginx (127.0.0.1), его в списке нет
        mvc.perform(from(get(url), "127.0.0.1")).andExpect(status().isUnauthorized());
    }

    @Test
    void forgedForwardedHeadersDoNotBypassTheList() throws Exception {
        String url = "/api/stations/static";
        // nginx с $proxy_add_x_forwarded_for дописывает настоящий адрес справа — левая запись клиента не в счёт
        mvc.perform(from(get(url), "127.0.0.1").header("X-Forwarded-For", "10.0.5.7, 203.0.113.9"))
                .andExpect(status().isUnauthorized());
        // nginx ставит только X-Real-IP, а X-Forwarded-For клиента пропускает — заголовки расходятся, адрес неизвестен
        mvc.perform(from(get(url), "127.0.0.1").header("X-Forwarded-For", "10.0.5.7").header("X-Real-IP", "203.0.113.9"))
                .andExpect(status().isUnauthorized());
        // и наоборот: X-Forwarded-For от nginx, X-Real-IP подложил клиент
        mvc.perform(from(get(url), "127.0.0.1").header("X-Forwarded-For", "203.0.113.9").header("X-Real-IP", "10.0.5.7"))
                .andExpect(status().isUnauthorized());
        // Мусор, имя узла, «unknown» и повтор заголовка — адрес неизвестен
        for (String forged : List.of("station-1.zavod.local", "unknown", "10.0.5.7.1", "")) {
            mvc.perform(from(get(url), "127.0.0.1").header("X-Forwarded-For", forged)).andExpect(status().isUnauthorized());
        }
        mvc.perform(from(get(url), "127.0.0.1").header("X-Real-IP", "10.0.5.7").header("X-Real-IP", "10.0.5.8"))
                .andExpect(status().isUnauthorized());
        // Цепочка из двух доверенных прокси: справа налево пропускаются только доверенные
        mvc.perform(from(get(url), "127.0.0.1").header("X-Forwarded-For", "10.0.5.7, 127.0.0.1"))
                .andExpect(status().isOk());
        mvc.perform(from(get(url), "127.0.0.1").header("X-Forwarded-For", "10.0.5.7:51234")).andExpect(status().isOk());
    }
}

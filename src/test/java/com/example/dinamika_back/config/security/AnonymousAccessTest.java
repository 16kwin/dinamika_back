package com.example.dinamika_back.config.security;

import com.example.dinamika_back.features.FeatureGateFilter;
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

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Пресет standard: функция {@code anonymous} выключена — слой потолка закрывает анониму прежнюю поверхность без входа
 * (401: нужно войти), без входа остаются только служебные адреса и флаги функций до входа. Вошедшему всё доступно,
 * как раньше. Отказы прежних правил не меняются (неизвестный путь анониму — по-прежнему 403). CORS — прежний список
 * плюс адреса из потолка, заголовок ревизии профиля открыт для чтения.
 */
@WebMvcTest(controllers = FeaturesController.class)
@Import({SecurityConfig.class, FeatureService.class, SecurityProbe.class})
@TestPropertySource(properties = {
        "awms.features.preset=standard",
        "awms.cors.allowed-origins=http://awms.example.test"})
class AnonymousAccessTest {

    private static final String ORIGIN = "http://awms.example.test";

    @Autowired
    private MockMvc mvc;
    @MockBean
    private AuthService authService;
    @MockBean
    private RefreshTokenService refreshTokenService;

    @Test
    void referenceBooksAndBoardSurfaceNeedSignIn() throws Exception {
        for (String url : List.of("/api/nomenclature/tree", "/api/templates/x", "/api/stations/crud/x",
                "/api/employees/1", "/api/locations/hierarchy", "/api/stations/static", "/api/stations/static/1",
                "/api/stations/dynamic", "/api/stations/dynamic/1", "/uploads/photo.png", "/api/dashboard/control-events",
                "/ws-stations/info", "/api/orders/active", "/api/tkp/active", "/api/locations/hierarchy/first")) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            mvc.perform(get(url).with(user("ivanov").authorities(() -> "OPERATOR"))).andExpect(status().isOk());
        }
        // HEAD справочника прежние правила анониму и раньше не открывали — отказ прежний
        mvc.perform(head("/api/nomenclature/tree")).andExpect(status().isForbidden());
        mvc.perform(post("/api/stations/static/filtered").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/stations/static/filtered").with(csrf()).with(user("ivanov").authorities(() -> "OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
    }

    @Test
    void serviceAddressesStayOpen() throws Exception {
        mvc.perform(get("/api/features/public")).andExpect(status().isOk());
        mvc.perform(get("/csrf")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/check_password").with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        // Отказы прежних правил не меняются: /api/features и неизвестный путь анониму — 403, как раньше
        mvc.perform(get("/api/features")).andExpect(status().isForbidden());
        mvc.perform(get("/api/unknown")).andExpect(status().isForbidden());
    }

    @Test
    void withoutBoardModeCallbacksAndCsrfExceptionsAreClosed() throws Exception {
        // orders и testsync в standard выключены: колбэков и приёма синхронизации без входа нет, исключений CSRF тоже
        mvc.perform(post("/api/orders/1/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/1/status").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/sync/receive").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        // Панели после входа — только с CSRF-токеном (исключение CSRF у /api/dashboard/** — только в режиме табло)
        mvc.perform(patch("/api/dashboard/economic/settings").with(user("ivanov").authorities(() -> "DIRECTOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/dashboard/economic/settings").with(user("ivanov").authorities(() -> "DIRECTOR"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        // SockJS-транспорт без CSRF-токена — только когда websocket включён (в standard он выключен)
        mvc.perform(post("/ws-stations/1/abc/xhr_send").with(user("ivanov").authorities(() -> "OPERATOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void corsKeepsServerAddressesAddsCeilingOnesAndExposesRevisionHeader() throws Exception {
        for (String origin : List.of(ORIGIN, "https://dynamikaawms.ru", "http://localhost:3000")) {
            mvc.perform(get("/api/features/public").header("Origin", origin))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin))
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                    .andExpect(header().string("Access-Control-Expose-Headers",
                            containsString(FeatureGateFilter.REVISION_HEADER)));
        }
        mvc.perform(options("/api/nomenclature/items").header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
        // Чужой адрес не получает ни данных, ни разрешения на изменение, в том числе вошедшего пользователя
        // и на рукопожатии живых обновлений (CORS Spring Security стоит перед /ws-stations)
        for (String url : List.of("/csrf", "/api/nomenclature/tree", "/ws-stations/info")) {
            mvc.perform(get(url).header("Origin", "http://evil.example").with(user("ivanov").authorities(() -> "OPERATOR")))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
        mvc.perform(options("/api/nomenclature/items").header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}

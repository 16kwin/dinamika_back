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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Пресет standard: функция {@code anonymous} выключена — без входа открыты только служебные адреса и флаги функций
 * до входа. Отказ анониму — 403, как и раньше (точка входа не задана, раздел 6.3 проекта). CORS — из потолка,
 * заголовок ревизии профиля открыт для чтения.
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
                "/api/locations/hierarchy", "/api/stations/static", "/api/stations/static/1", "/api/stations/dynamic",
                "/api/stations/dynamic/1", "/uploads/photo.png", "/api/dashboard/control-events", "/ws-stations/info",
                "/api/orders/active", "/api/tkp/active")) {
            mvc.perform(get(url)).andExpect(status().isForbidden());
            mvc.perform(get(url).with(user("ivanov").authorities(() -> "OPERATOR"))).andExpect(status().isOk());
        }
        mvc.perform(post("/api/stations/static/filtered").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void serviceAddressesStayOpen() throws Exception {
        mvc.perform(get("/api/features/public")).andExpect(status().isOk());
        mvc.perform(get("/csrf")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/check_password").with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/features")).andExpect(status().isForbidden());
    }

    @Test
    void withoutBoardModeCallbacksAndCsrfExceptionsAreClosed() throws Exception {
        // orders и testsync в standard выключены: колбэков и приёма синхронизации без входа нет
        mvc.perform(post("/api/orders/1/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/sync/receive").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        // Панели после входа — только с CSRF-токеном (исключение CSRF у /api/dashboard/** — только в режиме табло)
        mvc.perform(patch("/api/dashboard/economic/settings").with(user("ivanov").authorities(() -> "DIRECTOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/dashboard/economic/settings").with(user("ivanov").authorities(() -> "DIRECTOR"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
    }

    @Test
    void corsOriginsComeFromCeilingAndExposeRevisionHeader() throws Exception {
        mvc.perform(get("/api/features/public").header("Origin", ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString(FeatureGateFilter.REVISION_HEADER)));
        mvc.perform(options("/api/nomenclature/items").header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
        // Адрес разработчика из прежнего списка в коде больше не действует: список задаёт только потолок
        mvc.perform(get("/api/features/public").header("Origin", "http://localhost:3000"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
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

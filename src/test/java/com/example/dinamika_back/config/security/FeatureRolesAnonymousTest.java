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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Роли функций при пресете full: они сильнее режима табло (функция с ролями без входа недоступна — 401, даже если
 * её пути были анонимными), действуют роли функций из requires, колбэки внешней системы ролями не закрываются
 * (их защищает ключ OrdersCallbackKeyFilter).
 */
@WebMvcTest(controllers = FeaturesController.class)
@Import({SecurityConfig.class, FeatureService.class, SecurityProbe.class})
@TestPropertySource(properties = {
        "awms.features.preset=full",
        "awms.features.dashboards.roles=DIRECTOR",
        "awms.features.orders.roles=ADMIN",
        "awms.features.video.download.roles=ADMIN"})
class FeatureRolesAnonymousTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthService authService;
    @MockBean
    private RefreshTokenService refreshTokenService;

    private static RequestPostProcessor as(String role) {
        return user(role.toLowerCase()).authorities(() -> role);
    }

    @Test
    void rolesBeatBoardMode() throws Exception {
        // /api/dashboard/** в режиме табло анонимный, но у панелей роли — без входа нельзя
        mvc.perform(get("/api/dashboard/control-events")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard/control-events").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard/control-events").with(as("OPERATOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/control-events").with(as("DIRECTOR"))).andExpect(status().isOk());
        // Остальная поверхность табло прежняя
        mvc.perform(get("/api/stations/static")).andExpect(status().isOk());
        mvc.perform(get("/api/nomenclature/tree")).andExpect(status().isOk());
    }

    @Test
    void callbacksAreNotClosedByRoles() throws Exception {
        mvc.perform(post("/api/orders/1/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/tkp/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        // Прочие пути заказов вызывает фронтенд — по ролям
        mvc.perform(get("/api/orders/active")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders/active").with(as("DIRECTOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/active").with(as("ADMIN"))).andExpect(status().isOk());
        mvc.perform(post("/api/orders/1/cancel").with(as("OPERATOR"))).andExpect(status().isForbidden());
    }

    @Test
    void requiresRolesApplyToDependentFeature() throws Exception {
        // video.clips требует video.download: запись фрагмента — только ролям скачивания
        mvc.perform(post("/api/video/cameras/1/clips").with(as("DIRECTOR")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/video/cameras/1/clips").with(as("ADMIN")).with(csrf())).andExpect(status().isOk());
        mvc.perform(head("/api/video/files/1/download").with(as("DIRECTOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/video/files/1/content").with(as("DIRECTOR"))).andExpect(status().isOk());
        // Видео анониму не открывали и прежние правила — отказ прежний
        mvc.perform(get("/api/video/files/1/download")).andExpect(status().isForbidden());
    }
}

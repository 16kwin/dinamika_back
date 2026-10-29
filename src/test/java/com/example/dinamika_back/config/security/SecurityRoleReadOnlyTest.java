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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Роль SECURITY (администратор ИБ) вне профиля безопасности и переключателей только читает: обязанности разделены,
 * номенклатуру, станции и справочники она не правит. Профиль безопасности, вход и выход, выгрузки, личные настройки
 * вида и SockJS-транспорты — можно. Действует при любом потолке (роль новая, пользователей с ней до выключателей
 * не было); остальные роли не затронуты.
 */
@WebMvcTest(controllers = FeaturesController.class)
@Import({SecurityConfig.class, FeatureService.class, SecurityProbe.class})
class SecurityRoleReadOnlyTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthService authService;
    @MockBean
    private RefreshTokenService refreshTokenService;

    private static RequestPostProcessor as(String role) {
        return user("user-" + role.toLowerCase()).authorities(() -> role);
    }

    @Test
    void securityReadsButDoesNotChangeData() throws Exception {
        for (String role : List.of("SECURITY", "ROLE_SECURITY")) {
            mvc.perform(post("/api/nomenclature/items").with(as(role)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
            mvc.perform(delete("/api/nomenclature/items").with(as(role)).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(patch("/api/stations/crud/1").with(as(role)).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(put("/api/video/cameras/1").with(as(role)).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(post("/api/documents/refill/crud").with(as(role)).with(csrf())).andExpect(status().isForbidden());

            mvc.perform(get("/api/nomenclature/items").with(as(role))).andExpect(status().isOk());
            mvc.perform(head("/api/nomenclature/tree").with(as(role))).andExpect(status().isOk());
            mvc.perform(get("/api/features").with(as(role))).andExpect(status().isOk());
        }
    }

    @Test
    void securityWritesOnlyToProfileSwitchesAndAuth() throws Exception {
        RequestPostProcessor security = as("SECURITY");
        mvc.perform(post("/api/security-profile/events").with(security).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"REPORT_VIEWED\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/security-profile/purge").with(security).with(csrf())).andExpect(status().isOk());
        mvc.perform(put("/api/admin/features").with(security).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/auth/check_password").with(security).with(csrf())).andExpect(status().isOk());
        // Выгрузка — чтение: сервер рендерит присланную таблицу
        mvc.perform(post("/api/templates/export-pdf").with(security).with(csrf())).andExpect(status().isOk());
        // CSRF проверяется для SECURITY так же, как для всех
        mvc.perform(post("/api/security-profile/events").with(security)).andExpect(status().isForbidden());
    }

    @Test
    void securitySavesPersonalSettingsAndUsesSockJs() throws Exception {
        RequestPostProcessor security = as("SECURITY");
        // Личные настройки вида: столбцы, фильтры, сортировка таблиц, панель, сохранённые фильтры
        for (String url : List.of("/api/nomenclature/columns-settings", "/api/stations/filters-settings",
                "/api/documents/refill/sort-settings", "/api/templates/settings", "/api/dashboard/economic/settings")) {
            mvc.perform(patch(url).param("userId", "1").with(security).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
            // Только PATCH: другие методы на тех же путях — нет
            mvc.perform(put(url).param("userId", "1").with(security).with(csrf())).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/user/filters").with(security).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mvc.perform(delete("/api/user/filters").with(security).with(csrf())).andExpect(status().isOk());
        // Чтение методом POST
        mvc.perform(post("/api/stations/static/filtered").with(security).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        // SockJS: xhr_send и xhr_streaming (кадры SEND отбрасывает перехватчик WebSocketConfig)
        mvc.perform(post("/ws-stations/123/abcdef/xhr_send").with(security)).andExpect(status().isOk());
        mvc.perform(post("/ws-stations/123/abcdef/xhr_streaming").with(security)).andExpect(status().isOk());
        // Настройки — не повод менять данные: справочник и станции по-прежнему закрыты
        mvc.perform(patch("/api/stations/crud/1").with(security).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/stations/crud").with(security).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void otherRolesAreNotAffected() throws Exception {
        for (String role : List.of("ADMIN", "DIRECTOR", "OPERATOR")) {
            mvc.perform(post("/api/nomenclature/items").with(as(role)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
            mvc.perform(put("/api/video/cameras/1").with(as(role)).with(csrf())).andExpect(status().isOk());
        }
        // Профиль безопасности без входа закрыт
        mvc.perform(post("/api/security-profile/events").with(csrf())).andExpect(status().isForbidden());
    }
}

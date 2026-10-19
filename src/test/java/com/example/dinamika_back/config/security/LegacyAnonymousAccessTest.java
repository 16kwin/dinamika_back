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

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Пресет full (тестовый потолок, как на стенде): функция {@code anonymous} включена, и анонимная поверхность —
 * ровно прежняя (решение пользователя), без мёртвых правил: иерархия, станции, {@code /uploads/**},
 * {@code /ws-stations/**}, GET справочников, заказы и ТКП целиком, панели; те же исключения CSRF.
 * 3D-модели, план цеха и ГПП — по-прежнему только после входа.
 */
@WebMvcTest(controllers = FeaturesController.class)
@Import({SecurityConfig.class, FeatureService.class, SecurityProbe.class})
class LegacyAnonymousAccessTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthService authService;
    @MockBean
    private RefreshTokenService refreshTokenService;

    @Test
    void boardSurfaceIsOpenAsBefore() throws Exception {
        for (String url : List.of("/api/locations/hierarchy", "/api/stations/static", "/api/stations/static/1",
                "/api/stations/dynamic", "/api/stations/dynamic/1", "/uploads/photo.png", "/ws-stations/info",
                "/api/nomenclature/tree", "/api/nomenclature/models", "/api/templates/x", "/api/enterprises/1",
                "/api/workshops/1", "/api/sections/1", "/api/employees/1", "/api/station-types/x", "/api/station-manufacturers/x",
                "/api/station-models/x", "/api/station-configurations/x", "/api/stations/crud/x",
                "/api/orders/active", "/api/tkp/active", "/api/dashboard/control-events")) {
            mvc.perform(get(url)).andExpect(status().isOk());
        }
        // Поиск станций методом POST открыт, но, как и раньше, с CSRF-токеном
        mvc.perform(post("/api/stations/static/filtered").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/stations/dynamic/filtered").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void ordersTkpAndDashboardsKeepTheirCsrfExceptions() throws Exception {
        // Как раньше: заказы, ТКП и панели принимают изменения без входа и без CSRF-токена
        mvc.perform(post("/api/orders/1/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/orders/1/cancel").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/tkp/1/statusinvoice").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/dashboard/economic/settings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        // SockJS шлёт POST без CSRF-токена
        mvc.perform(post("/ws-stations/1/abc/xhr_send").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isOk());
    }

    @Test
    void changesAndClosedSectionsNeedSignIn() throws Exception {
        // Справочники меняются только после входа и с CSRF-токеном
        mvc.perform(delete("/api/nomenclature/items").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/nomenclature/items").with(user("ivanov"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/nomenclature/items").with(user("ivanov")).with(csrf())).andExpect(status().isOk());
        // HEAD справочников разрешающим правилом GET не покрыт — как раньше, только после входа
        mvc.perform(head("/api/nomenclature/tree")).andExpect(status().isForbidden());
        // 3D-модели, план цеха, ГПП — только после входа, даже в режиме табло
        for (String url : List.of("/api/nomenclature/1/models", "/api/nomenclature/models/3d/1/view",
                "/api/station-plans/workshops/1", "/api/gpp", "/api/video/status", "/api/features", "/api/unknown")) {
            mvc.perform(get(url)).andExpect(status().isForbidden());
            mvc.perform(get(url).with(user("ivanov"))).andExpect(status().isOk());
        }
        // testsync выключен и в full: приёма синхронизации без входа нет
        mvc.perform(post("/api/sync/receive").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deadRulesAreGone() throws Exception {
        // Правила для несуществующих контроллеров удалены: такие пути без входа закрыты, как любой неизвестный путь
        mvc.perform(get("/api/locations/hierarchy/first")).andExpect(status().isForbidden());
        mvc.perform(get("/api/locations/1/photo")).andExpect(status().isForbidden());
        mvc.perform(post("/api/stations").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        for (String url : List.of("/api/users/1", "/api/notification/1", "/api/animal_card/1", "/api/docs/1",
                "/api/information/1")) {
            mvc.perform(get(url)).andExpect(status().isForbidden());
        }
    }
}

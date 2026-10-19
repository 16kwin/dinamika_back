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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Роли функций из потолка ({@code awms.features.<key>.roles}) при пресете standard. Правила строятся
 * AntPathRequestMatcher: шаблон {@code /api/**}{@code /export-pdf} разбирается без PatternParseException (иначе
 * 500 на каждом запросе к правилу), правило GET закрывает и HEAD, роли родителя действуют на детей, а роли ребёнка
 * сужают доступ к его путям внутри родителя.
 */
@WebMvcTest(controllers = FeaturesController.class)
@Import({SecurityConfig.class, FeatureService.class, SecurityProbe.class})
@TestPropertySource(properties = {
        "awms.features.preset=standard",
        "awms.features.export.roles=ADMIN",
        "awms.features.dashboards.roles=ADMIN,DIRECTOR",
        "awms.features.dashboards.audit.roles=DIRECTOR",
        "awms.features.model3d.roles=ADMIN",
        "awms.features.uploads.roles=ADMIN",
        "awms.features.map.roles=DIRECTOR"})
class FeatureRolesTest {

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
    void exportOnlyForCeilingRoles() throws Exception {
        mvc.perform(post("/api/templates/export-pdf").with(as("OPERATOR")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/templates/export-pdf").with(as("DIRECTOR")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/templates/export-pdf").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/templates/export-pdf").with(as("ADMIN")).with(csrf())).andExpect(status().isOk());
        // ** в середине шаблона — на любой глубине; префикс ROLE_ в токене понимается так же
        mvc.perform(post("/api/stations/crud/export-excel").with(as("OPERATOR")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/stations/crud/print").with(as("ROLE_ADMIN")).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/documents/stock-level-control/crud/export-word").with(as("ADMIN")).with(csrf()))
                .andExpect(status().isOk());
        // Остальное ядро ролями функций не затронуто
        mvc.perform(get("/api/nomenclature/tree").with(as("OPERATOR"))).andExpect(status().isOk());
    }

    @Test
    void getRuleCoversHeadAndChildNarrowsParent() throws Exception {
        // dashboards: ADMIN, DIRECTOR; dashboards.audit (GET /api/dashboard/audit): только DIRECTOR
        mvc.perform(get("/api/dashboard/control-events").with(as("OPERATOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/control-events").with(as("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/control-events").with(as("DIRECTOR"))).andExpect(status().isOk());
        for (String role : new String[]{"OPERATOR", "ADMIN"}) {
            mvc.perform(get("/api/dashboard/audit").with(as(role))).andExpect(status().isForbidden());
            mvc.perform(head("/api/dashboard/audit").with(as(role))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/dashboard/audit").with(as("DIRECTOR"))).andExpect(status().isOk());
        mvc.perform(head("/api/dashboard/audit").with(as("DIRECTOR"))).andExpect(status().isOk());
        mvc.perform(head("/api/dashboard/audit")).andExpect(status().isForbidden());
    }

    @Test
    void parentWithoutRoutesStillLimitsChildren() throws Exception {
        // У карты (map) своих путей нет, её роли действуют на план цеха (map.plan) и правку расстановки (map.edit)
        mvc.perform(get("/api/station-plans/workshops/1").with(as("OPERATOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/station-plans/workshops/1").with(as("DIRECTOR"))).andExpect(status().isOk());
        mvc.perform(post("/api/station-plans/workshops/1/image").with(as("ADMIN")).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void model3dRolesLeaveBrandModelsBookOpen() throws Exception {
        mvc.perform(get("/api/nomenclature/1/models").with(as("OPERATOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/nomenclature/models/3d/1/view").with(as("OPERATOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/nomenclature/models/3d/1/view").with(as("ADMIN"))).andExpect(status().isOk());
        // Справочник моделей брендов — не 3D
        mvc.perform(get("/api/nomenclature/models").with(as("OPERATOR"))).andExpect(status().isOk());
    }

    @Test
    void uploadRolesCloseOnlyFileParts() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/nomenclature/1/images").file(file).with(as("OPERATOR")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/nomenclature/1/images").file(file).with(as("ADMIN")).with(csrf()))
                .andExpect(status().isOk());
        // Правило multipart не касается запросов без multipart
        mvc.perform(post("/api/nomenclature/1/images").with(as("OPERATOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        // Пути mixed (штрихкоды, поставки): форма без файла проходит, файл при чужой роли отклоняет сторож обработчика
        mvc.perform(multipart("/api/nomenclature/1/codes").param("codeValue", "123").with(as("OPERATOR")).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/nomenclature/1/supply").with(as("OPERATOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
    }
}

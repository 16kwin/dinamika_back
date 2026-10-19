package com.example.dinamika_back.features;

import com.example.dinamika_back.config.security.SecurityConfig;
import com.example.dinamika_back.service.AuthService;
import com.example.dinamika_back.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/features}: полный профиль — только после входа и с учётом ролей пользователя;
 * {@code /api/features/public} — анонимно и только флаги до входа (правило SecurityConfig из пакета B1).
 */
@WebMvcTest(controllers = FeaturesController.class)
@Import(SecurityConfig.class)
class FeaturesControllerTest {

    @TestConfiguration
    static class Profile {
        @Bean
        FeatureService featureService() {
            return new FeatureService(FeatureResolver.fromProperties(Map.of(
                    "awms.features.preset", "full",
                    "awms.features.export.roles", "ADMIN,DIRECTOR",
                    "awms.ui.inactivity-minutes", "15")));
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private FeatureService features;

    @MockBean
    private AuthService authService;
    @MockBean
    private RefreshTokenService refreshTokenService;

    @Test
    void publicFlagsWithoutSignIn() throws Exception {
        mvc.perform(get("/api/features/public"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.revision").value(features.revision()))
                .andExpect(jsonPath("$.features", aMapWithSize(2)))
                .andExpect(jsonPath("$.features.pwa").value(true))
                .andExpect(jsonPath("$.features.passwords").value(false));
    }

    @Test
    void fullProfileRequiresSignIn() throws Exception {
        mvc.perform(get("/api/features")).andExpect(status().isForbidden());
    }

    @Test
    void fullProfileAppliesRolesFromCeiling() throws Exception {
        mvc.perform(get("/api/features").with(user("operator").authorities(() -> "OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preset").value("full"))
                .andExpect(jsonPath("$.presetSource").value("test"))
                .andExpect(jsonPath("$.revision").value(features.revision()))
                .andExpect(jsonPath("$.features.video").value(true))
                .andExpect(jsonPath("$.features.export").value(false))
                .andExpect(jsonPath("$.features.testsync").value(false))
                .andExpect(jsonPath("$.features", not(hasKey("ai"))))
                .andExpect(jsonPath("$.ui.inactivityMinutes").value(15))
                .andExpect(jsonPath("$.ui.warningMinutes").value(1))
                .andExpect(jsonPath("$.canViewSecurity").value(false))
                .andExpect(jsonPath("$.canManage").value(false));

        mvc.perform(get("/api/features").with(user("director").authorities(() -> "DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.export").value(true))
                .andExpect(jsonPath("$.canViewSecurity").value(true));
    }

    @Test
    void noWriteMethods() throws Exception {
        mvc.perform(put("/api/features").with(user("admin").authorities(() -> "ADMIN")).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/features/public").with(csrf())).andExpect(status().isForbidden());
    }
}

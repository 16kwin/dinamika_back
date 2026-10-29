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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SecurityConfig «как до выключателей» (откат 19.10): прежние правила main не переписаны. Справочники
 * читаются без входа, меняются только после входа и с CSRF-токеном; флаги функций до входа открыты; CORS — прежний
 * жёсткий список сервера и стенда плюс дополнительные адреса из {@code awms.cors.allowed-origins}.
 * Слой правил потолка поверх них проверяют AccessMatrix*Test, AnonymousAccessTest, AnonymousNetworksTest,
 * FeatureRoles*Test и SecurityRoleReadOnlyTest.
 */
@WebMvcTest(controllers = FeaturesController.class)
@Import({SecurityConfig.class, FeatureService.class, SecurityProbe.class})
@TestPropertySource(properties = "awms.cors.allowed-origins=http://awms.example.test")
class SecurityConfigLegacyTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthService authService;
    @MockBean
    private RefreshTokenService refreshTokenService;

    @Test
    void referenceBooksAreReadableWithoutSignInButChangedOnlyAfterSignIn() throws Exception {
        for (String url : List.of("/api/nomenclature/tree", "/api/employees/1")) {
            mvc.perform(get(url)).andExpect(status().isOk());
        }
        mvc.perform(delete("/api/nomenclature/items")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/nomenclature/items").with(csrf())).andExpect(status().isForbidden());
        // Отказ выше — из-за входа, а не общий запрет: вошедший с CSRF-токеном проходит защиту
        mvc.perform(delete("/api/nomenclature/items").with(user("ivanov")).with(csrf())).andExpect(status().isOk());
    }

    @Test
    void publicFeatureFlagsAreOpenWithoutSignIn() throws Exception {
        mvc.perform(get("/api/features/public")).andExpect(status().isOk());
        mvc.perform(get("/api/features")).andExpect(status().isForbidden());
    }

    @Test
    void corsAllowsServerAndStandAddressesPlusExtraOnesButNotForeignSites() throws Exception {
        for (String origin : List.of("https://dynamikaawms.ru", "http://109.69.22.155:3000", "http://awms.example.test")) {
            mvc.perform(options("/api/nomenclature/items").header("Origin", origin)
                            .header("Access-Control-Request-Method", "DELETE"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin))
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        }
        mvc.perform(get("/api/features/public").header("Origin", "https://dynamikaawms.ru"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Expose-Headers", containsString(FeatureGateFilter.REVISION_HEADER)));
        mvc.perform(options("/api/nomenclature/items").header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(get("/api/nomenclature/tree").header("Origin", "http://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}

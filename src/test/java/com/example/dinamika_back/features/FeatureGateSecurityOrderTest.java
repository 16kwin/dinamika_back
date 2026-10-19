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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ворота стоят раньше Spring Security: закрытая функция отвечает 404 и анониму (без ворот было бы 403),
 * и администратору, на GET и HEAD; открытые пути проходят в Security как раньше и несут ревизию профиля.
 */
@WebMvcTest(controllers = FeaturesController.class)
@Import({SecurityConfig.class, FeatureWebConfig.class})
class FeatureGateSecurityOrderTest {

    @TestConfiguration
    static class Profile {
        @Bean
        FeatureService featureService() {
            return new FeatureService(FeatureResolver.fromProperties(Map.of("awms.features.preset", "isolated")));
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
    void closedFeatureAnswers404BeforeSecurity() throws Exception {
        mvc.perform(get("/api/video/status")).andExpect(status().isNotFound());
        mvc.perform(head("/api/video/files/1/download")).andExpect(status().isNotFound());
        mvc.perform(get("/api/video/status").with(user("admin").authorities(() -> "ADMIN"))).andExpect(status().isNotFound());
        // Мутирующий запрос без CSRF-токена тоже получает 404, а не 403 от CSRF-фильтра
        mvc.perform(post("/api/orders/1/status")).andExpect(status().isNotFound());
        mvc.perform(get("/ws-stations/info")).andExpect(status().isNotFound());
        mvc.perform(post("/api/nomenclature/1/images").with(csrf()).contentType("multipart/form-data; boundary=x"))
                .andExpect(status().isNotFound());
    }

    @Test
    void closedFeatureAnswersWithCorsHeadersOfSecurityConfig() throws Exception {
        // Фронт на другом адресе (localhost:3000 → API на localhost) должен увидеть 404, а не ошибку CORS:
        // ворота отвечают раньше CorsFilter и берут тот же бин corsConfigurationSource
        mvc.perform(get("/api/video/status").header("Origin", "http://localhost:3000"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/api/sync/receive").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mvc.perform(get("/api/video/status").header("Origin", "http://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void openPathsGoThroughSecurityWithRevision() throws Exception {
        mvc.perform(get("/api/features/public"))
                .andExpect(status().isOk())
                .andExpect(header().string(FeatureGateFilter.REVISION_HEADER, features.revision()));
        // Без входа полный профиль закрыт правилами Security, как любой непубличный путь
        mvc.perform(get("/api/features")).andExpect(status().isForbidden());
        mvc.perform(get("/api/features").with(user("operator").authorities(() -> "OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(header().string(FeatureGateFilter.REVISION_HEADER, features.revision()));
    }
}

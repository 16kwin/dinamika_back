package com.example.dinamika_back.features;

import com.example.dinamika_back.config.security.SecurityConfig;
import com.example.dinamika_back.features.journal.FeatureJournal;
import com.example.dinamika_back.features.journal.FeatureLogEntry;
import com.example.dinamika_back.features.report.SecurityProfileController;
import com.example.dinamika_back.features.report.SecurityProfileService;
import com.example.dinamika_back.service.AuthService;
import com.example.dinamika_back.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/security-profile} (раздел 6.7 проекта): отчёт — ролям ADMIN, DIRECTOR и SECURITY; события страницы —
 * те же роли, с CSRF, только четыре типа; одинаковое событие в течение минуты пишется один раз; методов записи
 * потолка нет.
 */
@WebMvcTest(controllers = SecurityProfileController.class)
@Import(SecurityConfig.class)
class SecurityProfileControllerTest {

    private static final String FINGERPRINT = "ab".repeat(32);

    @TestConfiguration
    static class Profile {
        @Bean
        FeatureService featureService() {
            return new FeatureService(FeatureResolver.fromProperties(Map.of("awms.features.preset", "standard")));
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private FeatureService features;

    @MockBean
    private SecurityProfileService reports;
    @MockBean
    private FeatureJournal journal;
    @MockBean
    private AuthService authService;
    @MockBean
    private RefreshTokenService refreshTokenService;

    @Test
    void reportOnlyForSecurityViewers() throws Exception {
        when(reports.report(any())).thenReturn(Map.of("header", Map.of("fingerprint", FINGERPRINT)));
        for (String role : new String[]{"ADMIN", "DIRECTOR", "SECURITY"}) {
            mvc.perform(get("/api/security-profile").with(as(role)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.header.fingerprint").value(FINGERPRINT));
        }
        mvc.perform(get("/api/security-profile").with(as("OPERATOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/security-profile")).andExpect(status().isForbidden());
        verify(reports, times(3)).report(any());
    }

    @Test
    void pageEventsAreJournaledOncePerMinute() throws Exception {
        FeatureLogEntry entry = new FeatureLogEntry();
        entry.setId(41L);
        entry.setRowHash("cd".repeat(32));
        when(journal.append(any())).thenReturn(entry);
        String body = "{\"type\":\"REPORT_PRINTED\",\"fingerprint\":\"" + features.ceiling().fingerprint()
                + "\",\"revision\":\"" + features.revision() + "\",\"details\":{\"pages\":7}}";

        mvc.perform(post("/api/security-profile/events").with(as("SECURITY")).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recorded").value(true))
                .andExpect(jsonPath("$.head.id").value(41));
        mvc.perform(post("/api/security-profile/events").with(as("SECURITY")).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recorded").value(false))
                .andExpect(jsonPath("$.duplicate").value(true));

        ArgumentCaptor<FeatureJournal.Event> captor = ArgumentCaptor.forClass(FeatureJournal.Event.class);
        verify(journal, times(1)).append(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(FeatureLogEntry.Type.REPORT_PRINTED);
    }

    @Test
    void wrongEventsAndWritesAreRejected() throws Exception {
        String startup = "{\"type\":\"STARTUP\"}";
        mvc.perform(post("/api/security-profile/events").with(as("ADMIN")).with(csrf())
                .contentType("application/json").content(startup)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/security-profile/events").with(as("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"type\":\"НЕТ\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/security-profile/events").with(as("ADMIN")).with(csrf()).contentType("application/json")
                .content("{\"type\":\"PROBE_RUN\",\"fingerprint\":\"не отпечаток\"}")).andExpect(status().isBadRequest());
        // Без CSRF и без роли
        mvc.perform(post("/api/security-profile/events").with(as("ADMIN")).contentType("application/json")
                .content("{\"type\":\"PROBE_RUN\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/security-profile/events").with(as("OPERATOR")).with(csrf())
                .contentType("application/json").content("{\"type\":\"PROBE_RUN\"}")).andExpect(status().isForbidden());
        // Записи профиля через API нет
        mvc.perform(put("/api/security-profile").with(as("SECURITY")).with(csrf())).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/security-profile").with(as("SECURITY")).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        verify(journal, never()).append(any());
    }

    private static RequestPostProcessor as(String role) {
        return user(role.toLowerCase() + "_test").authorities(() -> role);
    }
}

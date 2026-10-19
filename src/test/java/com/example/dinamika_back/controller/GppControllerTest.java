package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.GppSaveRequest;
import com.example.dinamika_back.repository.GppDocumentRepository;
import com.example.dinamika_back.repository.GppRowDependencyRepository;
import com.example.dinamika_back.repository.GppRowRepository;
import com.example.dinamika_back.repository.UserRepository;
import com.example.dinamika_back.service.GppService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ответы ГПП об ошибках — всегда { message }: огромное количество — 400 сразу, блокировка — 503, прочее — 500. */
class GppControllerTest {

    private static final String UID = UUID.randomUUID().toString();

    @BeforeEach
    void director() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "director_test", null, List.of(new SimpleGrantedAuthority("DIRECTOR"))));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void hugeQuantityIsBadRequestAtOnce() {
        GppDocumentRepository documents = mock(GppDocumentRepository.class);
        GppService service = new GppService(documents, mock(GppRowRepository.class),
                mock(GppRowDependencyRepository.class), mock(UserRepository.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new GppController(service)).build();
        // 23 байта числа: раньше — секунды и минуты процессора или 500 без message
        for (String quantity : List.of("1e999999999", "1e30000000")) {
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                director(); // проверка идёт в отдельном потоке, а контекст безопасности привязан к потоку
                mvc.perform(put("/api/gpp").contentType(MediaType.APPLICATION_JSON).content(body(quantity)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").value(
                                "Слишком большое количество строки «Изделие»: не больше 999 999 999 999,999"));
            });
        }
        verify(documents, never()).findFirstForUpdateByOrderByCreatedAtAsc();
    }

    @Test
    void lockTimeoutIsServiceUnavailable() throws Exception {
        GppService service = mock(GppService.class);
        when(service.save(any(GppSaveRequest.class), any())).thenThrow(
                new CannotAcquireLockException("ERROR: canceling statement due to lock timeout"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new GppController(service)).build();
        mvc.perform(put("/api/gpp").contentType(MediaType.APPLICATION_JSON).content(body("1")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("График сейчас записывает другой пользователь. Повторите запись через минуту."));
    }

    @Test
    void unexpectedErrorHasMessageWithoutDetails() throws Exception {
        GppService service = mock(GppService.class);
        when(service.save(any(GppSaveRequest.class), any())).thenThrow(
                new ArithmeticException("BigInteger would overflow supported range"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new GppController(service)).build();
        mvc.perform(put("/api/gpp").contentType(MediaType.APPLICATION_JSON).content(body("1")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Внутренняя ошибка сервера при обработке графика. Повторите попытку позже."));
    }

    private static String body(String quantity) {
        return "{\"version\":1,\"gppType\":\"GENERAL\",\"autoCalc\":true,\"periodFrom\":\"2027-01-01\",\"periodTo\":\"2027-07-31\","
                + "\"rows\":[{\"uid\":\"" + UID + "\",\"parentUid\":null,\"position\":0,\"name\":\"Изделие\",\"kind\":\"PRODUCT\","
                + "\"quantity\":" + quantity + ",\"unit\":\"шт\",\"dependsOn\":[]}]}";
    }
}

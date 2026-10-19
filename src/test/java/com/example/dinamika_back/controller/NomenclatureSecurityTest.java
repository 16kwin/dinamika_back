package com.example.dinamika_back.controller;

import com.example.dinamika_back.config.security.SecurityConfig;
import com.example.dinamika_back.features.FeatureService;
import com.example.dinamika_back.service.AuthService;
import com.example.dinamika_back.service.NomenclatureColumnSettingsService;
import com.example.dinamika_back.service.NomenclatureModelService;
import com.example.dinamika_back.service.NomenclatureService;
import com.example.dinamika_back.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Правила SecurityConfig для номенклатуры и справочников: читать можно без входа, изменять — только после входа
 * и с CSRF-токеном. Автор событий журнала (номенклатура и 3D-модели) — из входа. Кэш 3D-моделей — с проверкой
 * (no-cache + ETag).
 *
 * <p>FeatureService — потолок тестов (пресет full): NomenclatureController держит сторож файла в кодах и поставках.
 */
@WebMvcTest(controllers = {NomenclatureController.class, NomenclatureModelController.class})
@Import({SecurityConfig.class, FeatureService.class})
class NomenclatureSecurityTest {

    private static final String DELETE_BODY = "{\"materialUids\":[\"" + UUID.randomUUID() + "\"]}";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private NomenclatureService nomenclatureService;
    @MockBean
    private NomenclatureColumnSettingsService columnSettingsService;
    @MockBean
    private NomenclatureModelService modelService;
    @MockBean
    private AuthService authService;
    @MockBean
    private RefreshTokenService refreshTokenService;

    @Test
    void readingStaysOpenWithoutSignIn() throws Exception {
        when(nomenclatureService.getFullTree()).thenReturn(List.of());
        mvc.perform(get("/api/nomenclature/tree")).andExpect(status().isOk());
        mvc.perform(get("/api/nomenclature/models")).andExpect(status().isOk());
    }

    @Test
    void anonymousCannotDeleteNomenclature() throws Exception {
        mvc.perform(delete("/api/nomenclature/items").contentType(MediaType.APPLICATION_JSON).content(DELETE_BODY))
                .andExpect(status().isForbidden());
        // Токен CSRF без входа тоже не помогает (его можно получить анонимно через GET /csrf)
        mvc.perform(delete("/api/nomenclature/items").contentType(MediaType.APPLICATION_JSON).content(DELETE_BODY).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/nomenclature/groups/{uid}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\"}").with(csrf())).andExpect(status().isForbidden());
        verify(nomenclatureService, never()).deleteItems(any());
        verify(nomenclatureService, never()).renameGroup(any(), anyString());
    }

    @Test
    void signedInUserNeedsCsrfToken() throws Exception {
        mvc.perform(delete("/api/nomenclature/items").contentType(MediaType.APPLICATION_JSON).content(DELETE_BODY)
                .with(user("ivanov"))).andExpect(status().isForbidden());
        verify(nomenclatureService, never()).deleteItems(any());

        mvc.perform(delete("/api/nomenclature/items").contentType(MediaType.APPLICATION_JSON).content(DELETE_BODY)
                .with(user("ivanov")).with(csrf())).andExpect(status().isOk());
        verify(nomenclatureService).deleteItems(any());
    }

    @Test
    void otherReferenceBooksFollowTheSameRule() throws Exception {
        // Контроллеров этих справочников в срезе нет: 404 — запрос прошёл защиту, 403 — защита его отклонила
        for (String url : List.of("/api/templates/x", "/api/enterprises/1", "/api/workshops/1", "/api/sections/1",
                "/api/employees/1", "/api/station-types/x", "/api/station-manufacturers/x", "/api/station-models/x",
                "/api/station-configurations/x", "/api/stations/crud/x")) {
            mvc.perform(get(url)).andExpect(status().isNotFound());
            mvc.perform(delete(url).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(post(url).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(delete(url).with(user("ivanov"))).andExpect(status().isForbidden());
            mvc.perform(delete(url).with(user("ivanov")).with(csrf())).andExpect(status().isNotFound());
        }
        // Статусы заказов и ТКП SAAS присылает без входа и без CSRF — эти правила не менялись
        mvc.perform(post("/api/orders/1/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/tkp/1/statusinvoice").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void modelEventsAuthorComesFromSignInOnly() throws Exception {
        UUID material = UUID.randomUUID();
        when(modelService.authorName("ivanov")).thenReturn("Иванов Иван");
        MockMultipartFile file = new MockMultipartFile("file", "part.stl", "application/octet-stream", new byte[]{1, 2, 3});

        mvc.perform(multipart("/api/nomenclature/{uid}/models", material).file(file).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/nomenclature/{uid}/models", material).file(file).with(user("ivanov")))
                .andExpect(status().isForbidden());
        verify(modelService, never()).upload(any(), any(), any(), any(), any());

        // Старый клиент присылает author — запрос принимается, но подпись берётся из входа
        mvc.perform(multipart("/api/nomenclature/{uid}/models", material).file(file).param("author", "Петров Пётр")
                .with(user("ivanov")).with(csrf())).andExpect(status().isOk());
        verify(modelService).upload(eq(material), any(), isNull(), isNull(), eq("Иванов Иван"));

        UUID model = UUID.randomUUID();
        mvc.perform(delete("/api/nomenclature/models/3d/{uid}", model).param("author", "Петров Пётр")
                .with(user("ivanov"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/nomenclature/models/3d/{uid}", model).param("author", "Петров Пётр")
                .with(user("ivanov")).with(csrf())).andExpect(status().isNoContent());
        verify(modelService).delete(model, "Иванов Иван");
    }

    @Test
    void nomenclatureEventsAuthorComesFromSignInOnly() throws Exception {
        UUID material = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        when(modelService.authorName("ivanov")).thenReturn("Иванов Иван");
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});

        // Клиент подписывает «Оператор» или чужим ФИО — в журнал попадает вошедший пользователь
        mvc.perform(post("/api/nomenclature/draft").contentType(MediaType.APPLICATION_JSON)
                .content("{\"uid\":\"" + material + "\",\"author\":\"Петров Пётр\"}")
                .with(user("ivanov")).with(csrf())).andExpect(status().isOk());
        verify(nomenclatureService).saveDraft(any(), eq("Иванов Иван"));

        mvc.perform(multipart("/api/nomenclature/{uid}/images", material).file(file).param("author", "Петров Пётр")
                .with(user("ivanov")).with(csrf())).andExpect(status().isOk());
        verify(nomenclatureService).uploadImage(eq(material), any(), eq("Иванов Иван"));
        mvc.perform(multipart("/api/nomenclature/{uid}/blueprints", material).file(file).param("author", "Оператор")
                .with(user("ivanov")).with(csrf())).andExpect(status().isOk());
        verify(nomenclatureService).uploadBlueprint(eq(material), any(), eq("Иванов Иван"));
        mvc.perform(multipart("/api/nomenclature/{uid}/codes", material).param("codeValue", "123").param("author", "Оператор")
                .with(user("ivanov")).with(csrf())).andExpect(status().isOk());
        verify(nomenclatureService).uploadCode(eq(material), anyString(), eq("123"), anyString(), isNull(), eq("Иванов Иван"));
        mvc.perform(multipart("/api/nomenclature/{uid}/documents", material).file(file).param("documentName", "Паспорт")
                .param("author", "Оператор").with(user("ivanov")).with(csrf())).andExpect(status().isOk());
        verify(nomenclatureService).uploadDocument(eq(material), eq("Паспорт"), any(), eq("Иванов Иван"));
        mvc.perform(multipart("/api/nomenclature/{uid}/supply", material).param("supplierUid", uid.toString())
                .param("author", "Оператор").with(user("ivanov")).with(csrf())).andExpect(status().isOk());
        verify(nomenclatureService).addMaterialSupply(eq(material), any(), isNull(), eq("Иванов Иван"));

        for (String url : List.of("/api/nomenclature/images/{uid}", "/api/nomenclature/blueprints/{uid}",
                "/api/nomenclature/codes/{uid}", "/api/nomenclature/documents/{uid}", "/api/nomenclature/supply/{uid}")) {
            mvc.perform(delete(url, uid).param("author", "Петров Пётр").with(user("ivanov")).with(csrf()))
                    .andExpect(status().isOk());
        }
        verify(nomenclatureService).deleteImage(uid, "Иванов Иван");
        verify(nomenclatureService).deleteBlueprint(uid, "Иванов Иван");
        verify(nomenclatureService).deleteCode(uid, "Иванов Иван");
        verify(nomenclatureService).deleteDocument(uid, "Иванов Иван");
        verify(nomenclatureService).deleteMaterialSupply(uid, "Иванов Иван");
    }

    @Test
    void modelViewIsRevalidatedWithEtag(@TempDir Path dir) throws Exception {
        Path glb = dir.resolve("model.view.glb");
        byte[] content = new byte[100];
        for (int i = 0; i < content.length; i++) content[i] = (byte) i;
        Files.write(glb, content);
        UUID uid = UUID.randomUUID();
        when(modelService.viewFile(uid)).thenReturn(new NomenclatureModelService.ModelFile(glb, "model.glb", true));

        mvc.perform(get("/api/nomenclature/models/3d/{uid}/view", uid)).andExpect(status().isForbidden());

        String etag = mvc.perform(get("/api/nomenclature/models/3d/{uid}/view", uid).with(user("ivanov")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control",
                        allOf(containsString("no-cache"), containsString("private"), not(containsString("max-age")))))
                .andExpect(header().string("X-Model-View", "glb"))
                .andExpect(header().exists("ETag"))
                .andReturn().getResponse().getHeader("ETag");

        mvc.perform(get("/api/nomenclature/models/3d/{uid}/view", uid).with(user("ivanov")).header("If-None-Match", etag))
                .andExpect(status().isNotModified());
        // Без входа условный запрос тоже не проходит: кэш браузера не отдаст модель без проверки
        mvc.perform(get("/api/nomenclature/models/3d/{uid}/view", uid).header("If-None-Match", etag))
                .andExpect(status().isForbidden());

        // Докачка по частям (Range) работает как раньше
        mvc.perform(get("/api/nomenclature/models/3d/{uid}/view", uid).with(user("ivanov")).header("Range", "bytes=0-9"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 0-9/100"));

        // Файл перезаписан — ETag другой, старый не даёт 304
        Files.write(glb, new byte[120]);
        mvc.perform(get("/api/nomenclature/models/3d/{uid}/view", uid).with(user("ivanov")).header("If-None-Match", etag))
                .andExpect(status().isOk());
    }
}

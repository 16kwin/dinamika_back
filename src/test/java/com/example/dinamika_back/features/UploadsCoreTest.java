package com.example.dinamika_back.features;

import com.example.dinamika_back.config.security.SecurityConfig;
import com.example.dinamika_back.controller.NomenclatureController;
import com.example.dinamika_back.controller.SupplierController;
import com.example.dinamika_back.dto.CreateSupplyRequest;
import com.example.dinamika_back.service.AuthService;
import com.example.dinamika_back.service.NomenclatureColumnSettingsService;
import com.example.dinamika_back.service.NomenclatureModelService;
import com.example.dinamika_back.service.NomenclatureService;
import com.example.dinamika_back.service.RefreshTokenService;
import com.example.dinamika_back.service.SupplierService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ядро учёта без загрузок файлов (пакет B4): штрихкоды, QR, SKU и поставки уходят JSON и записываются при выключенной
 * функции {@code uploads}; файл в тех же формах (multipart) отклоняет сторож обработчика — 404, а пути загрузки
 * файлов закрывают ворота до Spring Security. Контракт JSON-вариантов для фронтенда (F4a, F4b):
 * <ul>
 *   <li>{@code POST /api/nomenclature/{uid}/codes} — {@code {codeType, codeValue, codeKind}}, умолчания QR_CODE и QR;</li>
 *   <li>{@code POST /api/nomenclature/{uid}/supply} — {@code {supplierUid, supplyDate, documentName}};</li>
 *   <li>{@code POST /api/suppliers/{uid}/deliveries} — {@code {materialUid, supplyDate, documentName}};</li>
 *   <li>дата — {@code 2026-10-14T09:30[:00]}, без даты — текущий момент; пустые поля можно не передавать.</li>
 * </ul>
 */
@WebMvcTest(controllers = {NomenclatureController.class, SupplierController.class})
@Import({SecurityConfig.class, FeatureWebConfig.class})
class UploadsCoreTest {

    private static final UUID MATERIAL = UUID.fromString("0b1c2d3e-0000-4000-8000-000000000001");
    private static final UUID SUPPLIER = UUID.fromString("0b1c2d3e-0000-4000-8000-000000000002");

    /** Потолок: full без загрузок файлов */
    @TestConfiguration
    static class Profile {
        @Bean
        FeatureService featureService() {
            return new FeatureService(FeatureResolver.fromProperties(Map.of(
                    "awms.features.preset", "full", "awms.features.uploads.enabled", "false")));
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private NomenclatureService nomenclatureService;
    @MockBean
    private NomenclatureColumnSettingsService columnSettingsService;
    @MockBean
    private NomenclatureModelService modelService;
    @MockBean
    private SupplierService supplierService;
    @MockBean
    private AuthService authService;
    @MockBean
    private RefreshTokenService refreshTokenService;

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "scan.pdf", "application/pdf", new byte[]{1, 2, 3});
    }

    // ==================== uploads=false: JSON проходит ====================

    @Test
    void codesWithoutFileAreSavedAsJson() throws Exception {
        when(modelService.authorName("ivanov")).thenReturn("Иванов Иван");
        mvc.perform(post("/api/nomenclature/{uid}/codes", MATERIAL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codeType\":\"EAN13\",\"codeValue\":\"4600000000017\",\"codeKind\":\"BARCODE\","
                                + "\"author\":\"Петров Пётр\"}")
                        .with(user("ivanov")).with(csrf()))
                .andExpect(status().isOk());
        verify(nomenclatureService).uploadCode(eq(MATERIAL), eq("EAN13"), eq("4600000000017"), eq("BARCODE"), isNull(),
                eq("Иванов Иван"));

        // Умолчания те же, что у формы: QR_CODE и QR
        mvc.perform(post("/api/nomenclature/{uid}/codes", MATERIAL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codeValue\":\"QR-7\",\"codeType\":\"\"}").with(user("ivanov")).with(csrf()))
                .andExpect(status().isOk());
        verify(nomenclatureService).uploadCode(eq(MATERIAL), eq("QR_CODE"), eq("QR-7"), eq("QR"), isNull(), eq("Иванов Иван"));
    }

    @Test
    void nomenclatureSupplyWithoutFileIsSavedAsJson() throws Exception {
        when(modelService.authorName("ivanov")).thenReturn("Иванов Иван");
        mvc.perform(post("/api/nomenclature/{uid}/supply", MATERIAL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierUid\":\"" + SUPPLIER + "\",\"supplyDate\":\"2026-10-14T09:30:00\","
                                + "\"documentName\":\"ТН-15\"}")
                        .with(user("ivanov")).with(csrf()))
                .andExpect(status().isOk());
        verify(nomenclatureService).addMaterialSupply(eq(MATERIAL), argThat((CreateSupplyRequest r) ->
                        SUPPLIER.equals(r.getSupplierUid()) && LocalDateTime.of(2026, 10, 14, 9, 30).equals(r.getSupplyDate())
                                && "ТН-15".equals(r.getDocumentName())),
                isNull(), eq("Иванов Иван"));

        // Без даты и документа (как шлёт фронтенд: null или поле пропущено)
        mvc.perform(post("/api/nomenclature/{uid}/supply", MATERIAL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierUid\":\"" + SUPPLIER + "\",\"supplyDate\":null,\"documentName\":null}")
                        .with(user("ivanov")).with(csrf()))
                .andExpect(status().isOk());
        verify(nomenclatureService).addMaterialSupply(eq(MATERIAL),
                argThat((CreateSupplyRequest r) -> r.getSupplyDate() == null && r.getDocumentName() == null),
                isNull(), eq("Иванов Иван"));
    }

    @Test
    void supplierDeliveryWithoutFileIsSavedAsJson() throws Exception {
        mvc.perform(post("/api/suppliers/{uid}/deliveries", SUPPLIER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialUid\":\"" + MATERIAL + "\",\"supplyDate\":\"2026-10-14T09:30:00\","
                                + "\"documentName\":\"ТН-15\"}")
                        .with(user("ivanov")).with(csrf()))
                .andExpect(status().isOk());
        verify(supplierService).addDelivery(eq(SUPPLIER), eq(MATERIAL), eq("2026-10-14T09:30"), eq("ТН-15"), isNull());

        // Позиция ассортимента: только материал
        mvc.perform(post("/api/suppliers/{uid}/deliveries", SUPPLIER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialUid\":\"" + MATERIAL + "\"}").with(user("ivanov")).with(csrf()))
                .andExpect(status().isOk());
        verify(supplierService).addDelivery(eq(SUPPLIER), eq(MATERIAL), isNull(), isNull(), isNull());
    }

    @Test
    void jsonWithoutRequiredUidOrWithBadDateIs400() throws Exception {
        mvc.perform(post("/api/nomenclature/{uid}/supply", MATERIAL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentName\":\"ТН-15\"}").with(user("ivanov")).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(post("/api/suppliers/{uid}/deliveries", SUPPLIER).contentType(MediaType.APPLICATION_JSON)
                .content("{}").with(user("ivanov")).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(post("/api/suppliers/{uid}/deliveries", SUPPLIER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialUid\":\"" + MATERIAL + "\",\"supplyDate\":\"14.10.2026\"}")
                        .with(user("ivanov")).with(csrf()))
                .andExpect(status().isBadRequest());
        verify(nomenclatureService, never()).addMaterialSupply(any(), any(), any(), any());
        verify(supplierService, never()).addDelivery(any(), any(), any(), any(), any());
    }

    @Test
    void jsonVariantsKeepSignInAndCsrfRules() throws Exception {
        String body = "{\"codeValue\":\"1\"}";
        mvc.perform(post("/api/nomenclature/{uid}/codes", MATERIAL).contentType(MediaType.APPLICATION_JSON).content(body)
                .with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/nomenclature/{uid}/codes", MATERIAL).contentType(MediaType.APPLICATION_JSON).content(body)
                .with(user("ivanov"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/suppliers/{uid}/deliveries", SUPPLIER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"materialUid\":\"" + MATERIAL + "\"}").with(user("ivanov"))).andExpect(status().isForbidden());
        verify(nomenclatureService, never()).uploadCode(any(), any(), any(), any(), any(), any());
        verify(supplierService, never()).addDelivery(any(), any(), any(), any(), any());
    }

    // ==================== uploads=false: файл — 404 ====================

    @Test
    void fileOnMixedPathIsRejectedByGuard() throws Exception {
        mvc.perform(multipart("/api/nomenclature/{uid}/codes", MATERIAL).file(file()).param("codeValue", "1")
                .with(user("ivanov")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/nomenclature/{uid}/supply", MATERIAL).file(file())
                .param("supplierUid", SUPPLIER.toString()).with(user("ivanov")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/suppliers/{uid}/deliveries", SUPPLIER).file(file())
                .param("materialUid", MATERIAL.toString()).with(user("ivanov")).with(csrf())).andExpect(status().isNotFound());
        verify(nomenclatureService, never()).uploadCode(any(), any(), any(), any(), any(), any());
        verify(nomenclatureService, never()).addMaterialSupply(any(), any(), any(), any());
        verify(supplierService, never()).addDelivery(any(), any(), any(), any(), any());
    }

    @Test
    void oldFormWithoutFileStillWorks() throws Exception {
        // Старый клиент шлёт multipart без файла — до пакета B4b такие запросы принимаются
        mvc.perform(multipart("/api/nomenclature/{uid}/codes", MATERIAL).param("codeValue", "123")
                .with(user("ivanov")).with(csrf())).andExpect(status().isOk());
        verify(nomenclatureService).uploadCode(eq(MATERIAL), eq("QR_CODE"), eq("123"), eq("QR"), isNull(), any());
        mvc.perform(multipart("/api/suppliers/{uid}/deliveries", SUPPLIER).param("materialUid", MATERIAL.toString())
                .with(user("ivanov")).with(csrf())).andExpect(status().isOk());
        verify(supplierService).addDelivery(eq(SUPPLIER), eq(MATERIAL), isNull(), isNull(), isNull());
        // Форма application/x-www-form-urlencoded тоже идёт в обработчик формы, а не в JSON-вариант
        mvc.perform(post("/api/nomenclature/{uid}/supply", MATERIAL).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("supplierUid", SUPPLIER.toString()).with(user("ivanov")).with(csrf())).andExpect(status().isOk());
        verify(nomenclatureService).addMaterialSupply(eq(MATERIAL), argThat((CreateSupplyRequest r) ->
                SUPPLIER.equals(r.getSupplierUid())), isNull(), any());
    }

    @Test
    void uploadPathsAreClosedByGateBeforeSecurity() throws Exception {
        for (String url : List.of("/api/nomenclature/{uid}/images", "/api/nomenclature/{uid}/blueprints",
                "/api/nomenclature/{uid}/documents")) {
            mvc.perform(multipart(url, MATERIAL).file(file()).param("documentName", "Паспорт")
                    .with(user("ivanov")).with(csrf())).andExpect(status().isNotFound());
            // Аноним без CSRF — тоже 404, а не 403: ворота стоят раньше Spring Security
            mvc.perform(multipart(url, MATERIAL).file(file())).andExpect(status().isNotFound());
        }
        mvc.perform(multipart("/api/suppliers/{uid}/images", SUPPLIER).file(file()).with(user("ivanov")).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(multipart("/api/suppliers/{uid}/documents", SUPPLIER).file(file()).param("documentName", "Устав")
                .with(user("ivanov")).with(csrf())).andExpect(status().isNotFound());
        verifyNoInteractions(nomenclatureService, supplierService);
    }

    // ==================== uploads=true и роли загрузки (без ворот и Security) ====================

    private static Authentication signedIn(String login, String role) {
        return new UsernamePasswordAuthenticationToken(login, null, List.of(new SimpleGrantedAuthority(role)));
    }

    private static MockMvc standalone(FeatureService features, NomenclatureService nomenclature, SupplierService suppliers) {
        NomenclatureModelService models = mock(NomenclatureModelService.class);
        when(models.authorName(anyString())).thenAnswer(call -> call.getArgument(0));
        return MockMvcBuilders.standaloneSetup(
                new NomenclatureController(nomenclature, mock(NomenclatureColumnSettingsService.class), models, features),
                new SupplierController(suppliers, features)).build();
    }

    @Test
    void uploadsOnAcceptsFileAndJson() throws Exception {
        NomenclatureService nomenclature = mock(NomenclatureService.class);
        SupplierService suppliers = mock(SupplierService.class);
        MockMvc mvc = standalone(new FeatureService(FeatureResolver.fromProperties(Map.of("awms.features.preset", "full"))),
                nomenclature, suppliers);
        Authentication operator = signedIn("ivanov", "OPERATOR");

        mvc.perform(multipart("/api/nomenclature/{uid}/supply", MATERIAL).file(file())
                .param("supplierUid", SUPPLIER.toString()).principal(operator)).andExpect(status().isOk());
        verify(nomenclature).addMaterialSupply(eq(MATERIAL), any(), notNull(), eq("ivanov"));
        mvc.perform(multipart("/api/suppliers/{uid}/deliveries", SUPPLIER).file(file())
                .param("materialUid", MATERIAL.toString()).principal(operator)).andExpect(status().isOk());
        verify(suppliers).addDelivery(eq(SUPPLIER), eq(MATERIAL), isNull(), isNull(), notNull());
        // JSON принимается и при включённых загрузках: фронтенд шлёт коды только так
        mvc.perform(post("/api/nomenclature/{uid}/codes", MATERIAL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codeValue\":\"SKU-1\",\"codeKind\":\"SKU\"}").principal(operator)).andExpect(status().isOk());
        verify(nomenclature).uploadCode(eq(MATERIAL), eq("QR_CODE"), eq("SKU-1"), eq("SKU"), isNull(), eq("ivanov"));
    }

    @Test
    void uploadRolesFromCeilingLimitFileButNotForm() throws Exception {
        NomenclatureService nomenclature = mock(NomenclatureService.class);
        SupplierService suppliers = mock(SupplierService.class);
        MockMvc mvc = standalone(new FeatureService(FeatureResolver.fromProperties(Map.of("awms.features.preset", "full",
                "awms.features.uploads.roles", "ADMIN"))), nomenclature, suppliers);

        // Чужая роль: файл — 403, та же поставка без файла проходит
        mvc.perform(multipart("/api/suppliers/{uid}/deliveries", SUPPLIER).file(file())
                .param("materialUid", MATERIAL.toString()).principal(signedIn("ivanov", "OPERATOR")))
                .andExpect(status().isForbidden());
        verify(suppliers, never()).addDelivery(any(), any(), any(), any(), any());
        mvc.perform(post("/api/suppliers/{uid}/deliveries", SUPPLIER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"materialUid\":\"" + MATERIAL + "\"}").principal(signedIn("ivanov", "OPERATOR")))
                .andExpect(status().isOk());
        // Роль из потолка — файл принимается (роль с префиксом ROLE_ тоже понимается)
        mvc.perform(multipart("/api/nomenclature/{uid}/codes", MATERIAL).file(file()).param("codeValue", "1")
                .principal(signedIn("admin", "ROLE_ADMIN"))).andExpect(status().isOk());
        verify(nomenclature).uploadCode(eq(MATERIAL), eq("QR_CODE"), eq("1"), eq("QR"), notNull(), eq("admin"));
    }
}

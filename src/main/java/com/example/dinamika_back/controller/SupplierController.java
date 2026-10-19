// SupplierController.java — ПОЛНЫЙ ФАЙЛ (добавлен renameDocument)
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.FeatureService;
import com.example.dinamika_back.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;
    /** Сторож файла в поставке, где файл необязателен: функция uploads */
    private final FeatureService features;

    @GetMapping("/generate")
    public ResponseEntity<SupplierCreateResponse> generate() {
        return ResponseEntity.ok(supplierService.generateCode());
    }

    @PostMapping("/draft")
    public ResponseEntity<Void> saveDraft(@RequestBody SupplierSaveRequest request) {
        supplierService.saveDraft(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<SprSupplierDTO>> getAll() {
        return ResponseEntity.ok(supplierService.getAllSuppliers());
    }

    @GetMapping("/{uid}")
    public ResponseEntity<SprSupplierDTO> getSupplier(@PathVariable UUID uid) {
        return ResponseEntity.ok(supplierService.getSupplier(uid));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> deleteSupplier(@PathVariable UUID uid) {
        supplierService.deleteSupplier(uid);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{supplierUid}/images")
    public ResponseEntity<List<SupplierMediaDTO>> getImages(@PathVariable UUID supplierUid) {
        return ResponseEntity.ok(supplierService.getImages(supplierUid));
    }

    @PostMapping("/{supplierUid}/images")
    public ResponseEntity<SupplierMediaDTO> uploadImage(@PathVariable UUID supplierUid, @RequestParam("file") MultipartFile file) {
        try { return ResponseEntity.ok(supplierService.uploadImage(supplierUid, file)); }
        catch (IOException e) { return ResponseEntity.internalServerError().build(); }
    }

    @DeleteMapping("/images/{uid}")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID uid) {
        supplierService.deleteImage(uid);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{supplierUid}/documents")
    public ResponseEntity<List<SupplierDocumentDTO>> getDocuments(@PathVariable UUID supplierUid) {
        return ResponseEntity.ok(supplierService.getDocuments(supplierUid));
    }

    @PostMapping("/{supplierUid}/documents")
    public ResponseEntity<SupplierDocumentDTO> uploadDocument(@PathVariable UUID supplierUid,
            @RequestParam("documentName") String documentName, @RequestParam("file") MultipartFile file) {
        try { return ResponseEntity.ok(supplierService.uploadDocument(supplierUid, documentName, file)); }
        catch (IOException e) { return ResponseEntity.internalServerError().build(); }
    }

    @PatchMapping("/documents/{uid}/rename")
    public ResponseEntity<SupplierDocumentDTO> renameDocument(
            @PathVariable UUID uid,
            @RequestParam("documentName") String documentName) {
        return ResponseEntity.ok(supplierService.renameDocument(uid, documentName));
    }

    @DeleteMapping("/documents/{uid}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID uid) {
        supplierService.deleteDocument(uid);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{supplierUid}/ratings")
    public ResponseEntity<List<SupplierRatingDTO>> getRatings(@PathVariable UUID supplierUid) {
        return ResponseEntity.ok(supplierService.getRatings(supplierUid));
    }

    @GetMapping("/{supplierUid}/ratings/average")
    public ResponseEntity<Double> getAverageRating(@PathVariable UUID supplierUid) {
        return ResponseEntity.ok(supplierService.getAverageRating(supplierUid));
    }

    @PostMapping("/{supplierUid}/ratings")
    public ResponseEntity<SupplierRatingDTO> addRating(@PathVariable UUID supplierUid, @RequestBody AddSupplierRatingRequest request) {
        return ResponseEntity.ok(supplierService.addRating(supplierUid, request));
    }

    @DeleteMapping("/ratings/{uid}")
    public ResponseEntity<Void> deleteRating(@PathVariable UUID uid) {
        supplierService.deleteRating(uid);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{supplierUid}/integrations")
    public ResponseEntity<List<SupplierIntegrationDTO>> getIntegrations(@PathVariable UUID supplierUid) {
        return ResponseEntity.ok(supplierService.getIntegrations(supplierUid));
    }

    @PostMapping("/{supplierUid}/integrations")
    public ResponseEntity<SupplierIntegrationDTO> addIntegration(@PathVariable UUID supplierUid,
            @RequestBody CreateSupplierIntegrationRequest request) {
        return ResponseEntity.ok(supplierService.addIntegration(supplierUid, request));
    }

    @DeleteMapping("/integrations/{uid}")
    public ResponseEntity<Void> deleteIntegration(@PathVariable UUID uid) {
        supplierService.deleteIntegration(uid);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/description-types")
    public ResponseEntity<List<SupplierDescriptionTypeDTO>> getDescriptionTypes() {
        return ResponseEntity.ok(supplierService.getDescriptionTypes());
    }

    @GetMapping("/{supplierUid}/events")
    public ResponseEntity<List<SupplierEventLogDTO>> getEvents(@PathVariable UUID supplierUid) {
        return ResponseEntity.ok(supplierService.getEvents(supplierUid));
    }

    // ==================== БРЕНДЫ ПОСТАВЩИКА ====================

    @GetMapping("/{supplierUid}/brands")
    public ResponseEntity<List<SupplierBrandDTO>> getBrands(@PathVariable UUID supplierUid) {
        return ResponseEntity.ok(supplierService.getBrands(supplierUid));
    }

    // ==================== ПОСТАВКИ ====================

    @GetMapping("/{supplierUid}/deliveries")
    public ResponseEntity<List<MaterialSupplyDTO>> getDeliveries(@PathVariable UUID supplierUid) {
        return ResponseEntity.ok(supplierService.getDeliveries(supplierUid));
    }

    /**
     * Поставка (и позиция ассортимента) из формы (multipart), файл документа необязателен; присланный файл
     * при выключенных загрузках — 404 (сторож). Запрос с телом JSON обслуживает {@link #addDeliveryJson}
     */
    @PostMapping("/{supplierUid}/deliveries")
    public ResponseEntity<MaterialSupplyDTO> addDelivery(
            @PathVariable UUID supplierUid,
            @RequestParam("materialUid") UUID materialUid,
            @RequestParam(value = "supplyDate", required = false) String supplyDate,
            @RequestParam(value = "documentName", required = false) String documentName,
            @RequestParam(value = "file", required = false) MultipartFile file,
            Authentication authentication) {
        requireUploadsFor(file, authentication);
        return saveDelivery(supplierUid, materialUid, supplyDate, documentName, file);
    }

    /** Поставка без файла: тело JSON, поля как у формы. Дата — {@code 2026-10-14T09:30[:00]}, без даты — текущий момент */
    public record DeliveryRequest(UUID materialUid, LocalDateTime supplyDate, String documentName) {
    }

    /**
     * Поставка и позиция ассортимента без файла: JSON {@code {materialUid, supplyDate, documentName}}.
     * Работает при любом составе функций, в том числе без загрузок файлов
     */
    @PostMapping(value = "/{supplierUid}/deliveries", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MaterialSupplyDTO> addDeliveryJson(@PathVariable UUID supplierUid,
                                                             @RequestBody DeliveryRequest request) {
        if (request.materialUid() == null) return ResponseEntity.badRequest().build();
        String supplyDate = request.supplyDate() != null ? request.supplyDate().toString() : null;
        return saveDelivery(supplierUid, request.materialUid(), supplyDate, request.documentName(), null);
    }

    private ResponseEntity<MaterialSupplyDTO> saveDelivery(UUID supplierUid, UUID materialUid, String supplyDate,
                                                           String documentName, MultipartFile file) {
        try {
            return ResponseEntity.ok(supplierService.addDelivery(supplierUid, materialUid, supplyDate, documentName, file));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Сторож формы, где файл необязателен (раздел 6.4 проекта): до пакета B4b ворота путь не закрывают.
     * Присланный файл при выключенных загрузках (uploads) — 404, как у закрытого пути загрузки; при ролях загрузки
     * в потолке ({@code awms.features.uploads.roles}) и чужой роли — 403. Без файла запрос проходит
     */
    private void requireUploadsFor(MultipartFile file, Authentication authentication) {
        if (file == null || file.isEmpty()) return;
        features.require(Feature.UPLOADS);
        if (!features.isOnFor(Feature.UPLOADS, authentication)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    @DeleteMapping("/deliveries/{uid}")
    public ResponseEntity<Void> deleteDelivery(@PathVariable UUID uid) {
        supplierService.deleteDelivery(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== АССОРТИМЕНТ ====================

    @GetMapping("/{supplierUid}/assortment")
    public ResponseEntity<List<MaterialItemDTO>> getAssortment(@PathVariable UUID supplierUid) {
        return ResponseEntity.ok(supplierService.getAssortment(supplierUid));
    }
}
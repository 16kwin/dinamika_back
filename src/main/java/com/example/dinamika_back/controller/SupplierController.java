// SupplierController.java — ПОЛНЫЙ ФАЙЛ
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    // ==================== Генерация кода ====================

    @GetMapping("/generate")
    public ResponseEntity<SupplierCreateResponse> generate() {
        return ResponseEntity.ok(supplierService.generateCode());
    }

    // ==================== Сохранение ====================

    @PostMapping("/draft")
    public ResponseEntity<Void> saveDraft(@RequestBody SupplierSaveRequest request) {
        supplierService.saveDraft(request);
        return ResponseEntity.ok().build();
    }

    // ==================== Получение всех ====================

    @GetMapping
    public ResponseEntity<List<SprSupplierDTO>> getAll() {
        return ResponseEntity.ok(supplierService.getAllSuppliers());
    }

    // ==================== Получение одного ====================

    @GetMapping("/{uid}")
    public ResponseEntity<SprSupplierDTO> getSupplier(@PathVariable UUID uid) {
        return ResponseEntity.ok(supplierService.getSupplier(uid));
    }

    // ==================== Удаление ====================

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> deleteSupplier(@PathVariable UUID uid) {
        supplierService.deleteSupplier(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== ИЗОБРАЖЕНИЯ ====================

    @GetMapping("/{supplierUid}/images")
    public ResponseEntity<List<SupplierMediaDTO>> getImages(@PathVariable UUID supplierUid) {
        return ResponseEntity.ok(supplierService.getImages(supplierUid));
    }

    @PostMapping("/{supplierUid}/images")
    public ResponseEntity<SupplierMediaDTO> uploadImage(
            @PathVariable UUID supplierUid,
            @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(supplierService.uploadImage(supplierUid, file));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/images/{uid}")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID uid) {
        supplierService.deleteImage(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== ДОКУМЕНТЫ ====================

    @GetMapping("/{supplierUid}/documents")
    public ResponseEntity<List<SupplierDocumentDTO>> getDocuments(@PathVariable UUID supplierUid) {
        return ResponseEntity.ok(supplierService.getDocuments(supplierUid));
    }

    @PostMapping("/{supplierUid}/documents")
    public ResponseEntity<SupplierDocumentDTO> uploadDocument(
            @PathVariable UUID supplierUid,
            @RequestParam("documentName") String documentName,
            @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(supplierService.uploadDocument(supplierUid, documentName, file));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/documents/{uid}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID uid) {
        supplierService.deleteDocument(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== РЕЙТИНГ ====================

    @GetMapping("/{supplierUid}/ratings")
    public ResponseEntity<List<SupplierRatingDTO>> getRatings(@PathVariable UUID supplierUid) {
        return ResponseEntity.ok(supplierService.getRatings(supplierUid));
    }

    @GetMapping("/{supplierUid}/ratings/average")
    public ResponseEntity<Double> getAverageRating(@PathVariable UUID supplierUid) {
        return ResponseEntity.ok(supplierService.getAverageRating(supplierUid));
    }

    @PostMapping("/{supplierUid}/ratings")
    public ResponseEntity<SupplierRatingDTO> addRating(
            @PathVariable UUID supplierUid,
            @RequestBody AddSupplierRatingRequest request) {
        return ResponseEntity.ok(supplierService.addRating(supplierUid, request));
    }

    @DeleteMapping("/ratings/{uid}")
    public ResponseEntity<Void> deleteRating(@PathVariable UUID uid) {
        supplierService.deleteRating(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== ИНТЕГРАЦИЯ ====================

    @GetMapping("/{supplierUid}/integrations")
    public ResponseEntity<List<SupplierIntegrationDTO>> getIntegrations(@PathVariable UUID supplierUid) {
        return ResponseEntity.ok(supplierService.getIntegrations(supplierUid));
    }

    @PostMapping("/{supplierUid}/integrations")
    public ResponseEntity<SupplierIntegrationDTO> addIntegration(
            @PathVariable UUID supplierUid,
            @RequestBody CreateSupplierIntegrationRequest request) {
        return ResponseEntity.ok(supplierService.addIntegration(supplierUid, request));
    }

    @DeleteMapping("/integrations/{uid}")
    public ResponseEntity<Void> deleteIntegration(@PathVariable UUID uid) {
        supplierService.deleteIntegration(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== ТИПЫ ОПИСАНИЙ ====================

    @GetMapping("/description-types")
    public ResponseEntity<List<SupplierDescriptionTypeDTO>> getDescriptionTypes() {
        return ResponseEntity.ok(supplierService.getDescriptionTypes());
    }
}
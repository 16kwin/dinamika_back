package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.ManufacturerColumnSettingsService;
import com.example.dinamika_back.service.ManufacturerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/manufacturers-crud")
@RequiredArgsConstructor
public class ManufacturerController {

    private final ManufacturerService manufacturerService;
    private final ManufacturerColumnSettingsService columnSettingsService;

    // ==================== CRUD ====================

    @GetMapping
    public ResponseEntity<ManufacturerListResponse> getAll(@RequestParam(required = false) Integer userId) {
        return ResponseEntity.ok(manufacturerService.getAllWithSettings(userId));
    }

    @GetMapping("/generate-code")
    public ResponseEntity<Integer> generateCode() {
        return ResponseEntity.ok(manufacturerService.generateCode());
    }

    @GetMapping("/{uid}")
    public ResponseEntity<Map<String, Object>> getManufacturer(@PathVariable UUID uid) {
        return ResponseEntity.ok(manufacturerService.getManufacturerData(uid));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateManufacturerRequest request) {
        return ResponseEntity.ok(manufacturerService.create(request));
    }

    @PatchMapping("/{uid}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable UUID uid, @RequestBody UpdateManufacturerRequest request) {
        return ResponseEntity.ok(manufacturerService.update(uid, request));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uid) {
        manufacturerService.delete(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== ИЗОБРАЖЕНИЯ ====================

    @GetMapping("/{uid}/images")
    public ResponseEntity<List<ManufacturerMediaDTO>> getImages(@PathVariable UUID uid) {
        return ResponseEntity.ok(manufacturerService.getImages(uid));
    }

    @PostMapping("/{uid}/images")
    public ResponseEntity<ManufacturerMediaDTO> uploadImage(@PathVariable UUID uid, @RequestParam("file") MultipartFile file) {
        try { return ResponseEntity.ok(manufacturerService.uploadImage(uid, file)); }
        catch (IOException e) { return ResponseEntity.internalServerError().build(); }
    }

    @DeleteMapping("/images/{imageUid}")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID imageUid) {
        manufacturerService.deleteImage(imageUid);
        return ResponseEntity.ok().build();
    }

    // ==================== ДОКУМЕНТЫ ====================

    @GetMapping("/{uid}/documents")
    public ResponseEntity<List<ManufacturerDocumentDTO>> getDocuments(@PathVariable UUID uid) {
        return ResponseEntity.ok(manufacturerService.getDocuments(uid));
    }

    @PostMapping("/{uid}/documents")
    public ResponseEntity<ManufacturerDocumentDTO> uploadDocument(@PathVariable UUID uid,
            @RequestParam("documentName") String documentName, @RequestParam("file") MultipartFile file) {
        try { return ResponseEntity.ok(manufacturerService.uploadDocument(uid, documentName, file)); }
        catch (IOException e) { return ResponseEntity.internalServerError().build(); }
    }

    @DeleteMapping("/documents/{documentUid}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID documentUid) {
        manufacturerService.deleteDocument(documentUid);
        return ResponseEntity.ok().build();
    }

    // ==================== Events ====================

    @GetMapping("/events")
    public ResponseEntity<List<ManufacturerEventLogDto>> getAllEvents() {
        return ResponseEntity.ok(manufacturerService.getAllEvents());
    }

    @GetMapping("/{uid}/events")
    public ResponseEntity<List<ManufacturerEventLogDto>> getEvents(@PathVariable UUID uid) {
        return ResponseEntity.ok(manufacturerService.getEvents(uid));
    }

    // ==================== Settings ====================

    @GetMapping("/settings")
    public ResponseEntity<Map<String, String>> getAllSettings(@RequestParam Integer userId) {
        Map<String, String> settings = Map.of(
                "columnsJson", columnSettingsService.getColumnsJson(userId) != null
                        ? columnSettingsService.getColumnsJson(userId) : "{}",
                "filtersJson", columnSettingsService.getFiltersJson(userId),
                "sortJson", columnSettingsService.getSortJson(userId)
        );
        return ResponseEntity.ok(settings);
    }

    @GetMapping("/columns-settings")
    public ResponseEntity<String> getColumnsSettings(@RequestParam Integer userId) {
        String json = columnSettingsService.getColumnsJson(userId);
        return ResponseEntity.ok(json != null ? json : "{}");
    }

    @PatchMapping("/columns-settings")
    public ResponseEntity<Void> saveColumnsSettings(@RequestParam Integer userId, @RequestBody Map<String, Object> body) {
        String columnsJson = (String) body.get("columnsJson");
        columnSettingsService.saveColumnsJson(userId, columnsJson);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/filters-settings")
    public ResponseEntity<String> getFiltersSettings(@RequestParam Integer userId) {
        return ResponseEntity.ok(columnSettingsService.getFiltersJson(userId));
    }

    @PatchMapping("/filters-settings")
    public ResponseEntity<Void> saveFiltersSettings(@RequestParam Integer userId, @RequestBody Map<String, Object> body) {
        String filtersJson = (String) body.get("filtersJson");
        columnSettingsService.saveFiltersJson(userId, filtersJson);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/sort-settings")
    public ResponseEntity<String> getSortSettings(@RequestParam Integer userId) {
        return ResponseEntity.ok(columnSettingsService.getSortJson(userId));
    }

    @PatchMapping("/sort-settings")
    public ResponseEntity<Void> saveSortSettings(@RequestParam Integer userId, @RequestBody Map<String, Object> body) {
        String sortJson = (String) body.get("sortJson");
        columnSettingsService.saveSortJson(userId, sortJson);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/settings")
    public ResponseEntity<Void> saveAllSettings(@RequestParam Integer userId, @RequestBody Map<String, Object> body) {
        String columnsJson = (String) body.get("columnsJson");
        String filtersJson = (String) body.get("filtersJson");
        String sortJson = (String) body.get("sortJson");
        columnSettingsService.saveAllJson(userId, columnsJson, filtersJson, sortJson);
        return ResponseEntity.ok().build();
    }
}
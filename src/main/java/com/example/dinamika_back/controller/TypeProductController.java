package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.TypeProductColumnSettingsService;
import com.example.dinamika_back.service.TypeProductService;
import com.example.dinamika_back.service.OfficeExportService;
import com.example.dinamika_back.service.PdfExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/type-products")
@RequiredArgsConstructor
public class TypeProductController {

    private final TypeProductService typeProductService;
    private final TypeProductColumnSettingsService columnSettingsService;
    private final PdfExportService pdfExportService;
    private final OfficeExportService officeExportService;

    // ==================== CRUD ====================

    @GetMapping
    public ResponseEntity<TypeProductListResponse> getAll(@RequestParam(required = false) Integer userId) {
        return ResponseEntity.ok(typeProductService.getAllWithSettings(userId));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateTypeProductRequest request) {
        return ResponseEntity.ok(typeProductService.create(request));
    }

    @PatchMapping("/{uid}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable UUID uid,
            @RequestBody UpdateTypeProductRequest request) {
        return ResponseEntity.ok(typeProductService.update(uid, request));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uid) {
        typeProductService.delete(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== Events ====================

    @GetMapping("/events")
    public ResponseEntity<List<TypeProductEventLogDto>> getAllEvents() {
        return ResponseEntity.ok(typeProductService.getAllEvents());
    }

    @GetMapping("/{uid}/events")
    public ResponseEntity<List<TypeProductEventLogDto>> getEvents(@PathVariable UUID uid) {
        return ResponseEntity.ok(typeProductService.getEvents(uid));
    }

    // ==================== Settings ====================

    @GetMapping("/settings")
    public ResponseEntity<Map<String, String>> getAllSettings(@RequestParam Integer userId) {
        Map<String, String> settings = Map.of(
                "columnsJson", columnSettingsService.getColumnsJson(userId) != null
                        ? columnSettingsService.getColumnsJson(userId)
                        : "{}",
                "filtersJson", columnSettingsService.getFiltersJson(userId),
                "sortJson", columnSettingsService.getSortJson(userId));
        return ResponseEntity.ok(settings);
    }

    @GetMapping("/columns-settings")
    public ResponseEntity<String> getColumnsSettings(@RequestParam Integer userId) {
        String json = columnSettingsService.getColumnsJson(userId);
        return ResponseEntity.ok(json != null ? json : "{}");
    }

    @PatchMapping("/columns-settings")
    public ResponseEntity<Void> saveColumnsSettings(@RequestParam Integer userId,
            @RequestBody Map<String, Object> body) {
        String columnsJson = (String) body.get("columnsJson");
        columnSettingsService.saveColumnsJson(userId, columnsJson);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/filters-settings")
    public ResponseEntity<String> getFiltersSettings(@RequestParam Integer userId) {
        return ResponseEntity.ok(columnSettingsService.getFiltersJson(userId));
    }

    @PatchMapping("/filters-settings")
    public ResponseEntity<Void> saveFiltersSettings(@RequestParam Integer userId,
            @RequestBody Map<String, Object> body) {
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

    // ==================== ПДФ, ПЕЧАТЬ, EXCEL, WORD ====================

    @PostMapping("/export-pdf")
    public ResponseEntity<byte[]> exportPdf(@RequestBody Map<String, Object> request) throws Exception {
        String title = (String) request.get("title");
        List<String> columns = (List<String>) request.get("columns");
        List<String> columnLabels = (List<String>) request.get("columnLabels");
        List<Map<String, Object>> data = (List<Map<String, Object>>) request.get("data");
        boolean landscape = (boolean) request.getOrDefault("landscape", false);
        List<String> footerLines = (List<String>) request.get("footerLines");

        byte[] pdf = pdfExportService.generatePdf(title, columns, columnLabels, data, landscape, footerLines);
        return buildPdfResponse(pdf, "export.pdf", false);
    }

    @PostMapping("/print")
    public ResponseEntity<byte[]> print(@RequestBody Map<String, Object> request) throws Exception {
        String title = (String) request.get("title");
        List<String> columns = (List<String>) request.get("columns");
        List<String> columnLabels = (List<String>) request.get("columnLabels");
        List<Map<String, Object>> data = (List<Map<String, Object>>) request.get("data");
        boolean landscape = (boolean) request.getOrDefault("landscape", false);
        List<String> footerLines = (List<String>) request.get("footerLines");

        byte[] pdf = pdfExportService.generatePdf(title, columns, columnLabels, data, landscape, footerLines);
        return buildPdfResponse(pdf, "print.pdf", true);
    }

    @PostMapping("/export-excel")
    public ResponseEntity<byte[]> exportExcel(@RequestBody Map<String, Object> request) throws Exception {
        String title = (String) request.get("title");
        List<String> columns = (List<String>) request.get("columns");
        List<String> columnLabels = (List<String>) request.get("columnLabels");
        List<Map<String, Object>> data = (List<Map<String, Object>>) request.get("data");
        List<String> footerLines = (List<String>) request.get("footerLines");

        byte[] excel = officeExportService.exportExcel(title, columns, columnLabels, data, footerLines);
        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"type-products.xlsx\"")
                .body(excel);
    }

    @PostMapping("/export-word")
    public ResponseEntity<byte[]> exportWord(@RequestBody Map<String, Object> request) throws Exception {
        String title = (String) request.get("title");
        List<String> columns = (List<String>) request.get("columns");
        List<String> columnLabels = (List<String>) request.get("columnLabels");
        List<Map<String, Object>> data = (List<Map<String, Object>>) request.get("data");
        List<String> footerLines = (List<String>) request.get("footerLines");

        byte[] word = officeExportService.exportWord(title, columns, columnLabels, data, footerLines);
        return ResponseEntity.ok()
                .contentType(MediaType
                        .parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"type-products.docx\"")
                .body(word);
    }

    private ResponseEntity<byte[]> buildPdfResponse(byte[] pdf, String filename, boolean inline) {
        String contentDisposition = inline
                ? "inline; filename=\"" + filename + "\""
                : "attachment; filename=\"" + filename + "\"";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(pdf);
    }
}
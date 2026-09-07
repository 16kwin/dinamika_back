// EnterpriseController.java — ПОЛНЫЙ ФАЙЛ (добавлены эндпоинты для печати и экспорта)
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.EnterpriseService;
import com.example.dinamika_back.service.EnterpriseColumnSettingsService;
import com.example.dinamika_back.service.OfficeExportService;
import com.example.dinamika_back.service.PdfExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/enterprises")
@RequiredArgsConstructor
public class EnterpriseController {

    private final EnterpriseService enterpriseService;
    private final EnterpriseColumnSettingsService columnSettingsService;
    private final PdfExportService pdfExportService;
    private final OfficeExportService officeExportService;

    // ==================== CRUD ====================

    @GetMapping
    public ResponseEntity<EnterpriseListResponse> getAll(@RequestParam(required = false) Integer userId,
            @RequestParam(required = false) Long holdingId) {
        if (holdingId != null) {
            List<EnterpriseFlatDto> byHolding = enterpriseService.getByHoldingId(holdingId);
            EnterpriseListResponse response = new EnterpriseListResponse();
            response.setColumns(List.of("name", "description", "address", "holdingName", "locationName"));
            response.setData(byHolding);
            response.setColumnWidths(Map.of());
            response.setRequiredColumns(List.of("name"));
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.ok(enterpriseService.getAllWithSettings(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnterpriseFlatDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(enterpriseService.getById(id));
    }

    @PostMapping
    public ResponseEntity<EnterpriseFlatDto> create(@RequestBody CreateEnterpriseRequest request) {
        return ResponseEntity.ok(enterpriseService.create(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<EnterpriseFlatDto> update(@PathVariable Long id,
            @RequestBody UpdateEnterpriseRequest request) {
        return ResponseEntity.ok(enterpriseService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        enterpriseService.delete(id);
        return ResponseEntity.ok().build();
    }

    // ==================== Events ====================

    @GetMapping("/events")
    public ResponseEntity<List<EnterpriseEventLogDto>> getAllEvents() {
        return ResponseEntity.ok(enterpriseService.getAllEvents());
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<List<EnterpriseEventLogDto>> getEvents(@PathVariable Long id) {
        return ResponseEntity.ok(enterpriseService.getEvents(id));
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
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"enterprises.xlsx\"")
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
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"enterprises.docx\"")
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
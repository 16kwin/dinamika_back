// SectionController.java — ПОЛНЫЙ ФАЙЛ (с getAllWithSettings)
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.SectionService;
import com.example.dinamika_back.service.SectionColumnSettingsService;
import lombok.RequiredArgsConstructor;
import com.example.dinamika_back.service.PdfExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sections")
@RequiredArgsConstructor
public class SectionController {

    private final SectionService sectionService;
    private final SectionColumnSettingsService columnSettingsService;
    private final PdfExportService pdfExportService;
    // ==================== CRUD ====================

    @GetMapping
    public ResponseEntity<SectionListResponse> getAll(@RequestParam(required = false) Integer userId,
            @RequestParam(required = false) Long workshopId) {
        if (workshopId != null) {
            List<SectionFlatDto> byWorkshop = sectionService.getByWorkshopId(workshopId);
            SectionListResponse response = new SectionListResponse();
            response.setColumns(List.of("name", "workshopName", "enterpriseName", "holdingName"));
            response.setData(byWorkshop);
            response.setColumnWidths(Map.of());
            response.setRequiredColumns(List.of("name"));
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.ok(sectionService.getAllWithSettings(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SectionFlatDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(sectionService.getById(id));
    }

    @PostMapping
    public ResponseEntity<SectionFlatDto> create(@RequestBody CreateSectionRequest request) {
        return ResponseEntity.ok(sectionService.create(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SectionFlatDto> update(@PathVariable Long id, @RequestBody UpdateSectionRequest request) {
        return ResponseEntity.ok(sectionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        sectionService.delete(id);
        return ResponseEntity.ok().build();
    }

    // ==================== ПДФ И ПЕЧТАТЬ ====================
    @PostMapping("/crud/export-pdf")
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

    @PostMapping("/crud/print")
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

    private ResponseEntity<byte[]> buildPdfResponse(byte[] pdf, String filename, boolean inline) {
        String contentDisposition = inline
                ? "inline; filename=\"" + filename + "\""
                : "attachment; filename=\"" + filename + "\"";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(pdf);
    }

    // ==================== Events ====================

    @GetMapping("/events")
    public ResponseEntity<List<SectionEventLogDto>> getAllEvents() {
        return ResponseEntity.ok(sectionService.getAllEvents());
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<List<SectionEventLogDto>> getEvents(@PathVariable Long id) {
        return ResponseEntity.ok(sectionService.getEvents(id));
    }

    // ==================== All Settings ====================

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

    // ==================== Column Settings ====================

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

    // ==================== Filters Settings ====================

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

    // ==================== Sort Settings ====================

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
}
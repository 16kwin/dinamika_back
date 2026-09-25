// TemplateController.java — ПОЛНЫЙ ФАЙЛ (с эндпоинтами истории)
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.TemplateService;
import com.example.dinamika_back.service.TemplateColumnSettingsService;
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
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final TemplateColumnSettingsService columnSettingsService;
    private final PdfExportService pdfExportService;
    private final OfficeExportService officeExportService;

    // ==================== НАЗНАЧЕНИЯ ЯЧЕЕК ====================

    @GetMapping("/cell-assignments")
    public ResponseEntity<List<CellAssignmentDto>> getAllCellAssignments() {
        return ResponseEntity.ok(templateService.getAllCellAssignments());
    }

    // ==================== ИСТОРИЯ ИЗМЕНЕНИЙ ====================

    @GetMapping("/events")
    public ResponseEntity<List<TemplateEventLogDto>> getAllEvents() {
        return ResponseEntity.ok(templateService.getAllEvents());
    }

    @GetMapping("/{uid}/events")
    public ResponseEntity<List<TemplateEventLogDto>> getTemplateEvents(@PathVariable UUID uid) {
        return ResponseEntity.ok(templateService.getEvents(uid));
    }

    // ==================== ДЕРЕВО С НАСТРОЙКАМИ ====================

    @GetMapping("/tree-with-settings")
    public ResponseEntity<TemplatesTreeResponse> getTreeWithSettings(@RequestParam Integer userId) {
        return ResponseEntity.ok(templateService.getTreeWithSettings(userId));
    }

    // ==================== НАСТРОЙКИ ====================

    @GetMapping("/settings")
    public ResponseEntity<Map<String, String>> getAllSettings(@RequestParam Integer userId) {
        return ResponseEntity.ok(Map.of(
                "columnsJson", nullSafe(columnSettingsService.getColumnsJson(userId)),
                "filtersJson", nullSafe(columnSettingsService.getFiltersJson(userId)),
                "sortJson", nullSafe(columnSettingsService.getSortJson(userId)),
                "currentPathJson", nullSafe(columnSettingsService.getCurrentPathJson(userId))
        ));
    }

    @PatchMapping("/settings")
    public ResponseEntity<Void> saveAllSettings(@RequestParam Integer userId,
                                                @RequestBody Map<String, String> body) {
        if (body.containsKey("columnsJson")) columnSettingsService.saveColumnsJson(userId, body.get("columnsJson"));
        if (body.containsKey("filtersJson")) columnSettingsService.saveFiltersJson(userId, body.get("filtersJson"));
        if (body.containsKey("sortJson")) columnSettingsService.saveSortJson(userId, body.get("sortJson"));
        if (body.containsKey("currentPathJson")) columnSettingsService.saveCurrentPathJson(userId, body.get("currentPathJson"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/columns-settings")
    public ResponseEntity<Map<String, String>> getColumnsSettings(@RequestParam Integer userId) {
        return ResponseEntity.ok(Map.of("columnsJson", nullSafe(columnSettingsService.getColumnsJson(userId))));
    }

    @PatchMapping("/columns-settings")
    public ResponseEntity<Void> saveColumnsSettings(@RequestParam Integer userId,
                                                    @RequestBody Map<String, String> body) {
        columnSettingsService.saveColumnsJson(userId, body.get("columnsJson"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/filters-settings")
    public ResponseEntity<Map<String, String>> getFiltersSettings(@RequestParam Integer userId) {
        return ResponseEntity.ok(Map.of("filtersJson", nullSafe(columnSettingsService.getFiltersJson(userId))));
    }

    @PatchMapping("/filters-settings")
    public ResponseEntity<Void> saveFiltersSettings(@RequestParam Integer userId,
                                                    @RequestBody Map<String, String> body) {
        columnSettingsService.saveFiltersJson(userId, body.get("filtersJson"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sort-settings")
    public ResponseEntity<Map<String, String>> getSortSettings(@RequestParam Integer userId) {
        return ResponseEntity.ok(Map.of("sortJson", nullSafe(columnSettingsService.getSortJson(userId))));
    }

    @PatchMapping("/sort-settings")
    public ResponseEntity<Void> saveSortSettings(@RequestParam Integer userId,
                                                 @RequestBody Map<String, String> body) {
        columnSettingsService.saveSortJson(userId, body.get("sortJson"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/current-path")
    public ResponseEntity<Map<String, String>> getCurrentPath(@RequestParam Integer userId) {
        return ResponseEntity.ok(Map.of("currentPathJson", nullSafe(columnSettingsService.getCurrentPathJson(userId))));
    }

    @PatchMapping("/current-path")
    public ResponseEntity<Void> saveCurrentPath(@RequestParam Integer userId,
                                                @RequestBody Map<String, String> body) {
        columnSettingsService.saveCurrentPathJson(userId, body.get("currentPathJson"));
        return ResponseEntity.noContent().build();
    }

    private String nullSafe(String s) {
        return s != null ? s : "{}";
    }

    // ==================== КАТЕГОРИИ ====================

    @GetMapping("/categories")
    public ResponseEntity<List<TemplateCategoryDto>> getAllCategories() {
        return ResponseEntity.ok(templateService.getAllCategories());
    }

    @GetMapping("/categories/{id}")
    public ResponseEntity<TemplateCategoryDto> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.getCategoryById(id));
    }

    @GetMapping("/categories/uid/{uid}")
    public ResponseEntity<TemplateCategoryDto> getCategoryByUid(@PathVariable UUID uid) {
        return ResponseEntity.ok(templateService.getCategoryByUid(uid));
    }

    @PostMapping("/categories")
    public ResponseEntity<TemplateCategoryDto> createCategory(@RequestBody CreateTemplateCategoryRequest request) {
        return ResponseEntity.ok(templateService.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<TemplateCategoryDto> updateCategory(@PathVariable Long id,
            @RequestBody CreateTemplateCategoryRequest request) {
        return ResponseEntity.ok(templateService.updateCategory(id, request));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        templateService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/categories/move")
    public ResponseEntity<TemplateCategoryDto> moveCategory(@RequestBody MoveCategoryRequest request) {
        return ResponseEntity.ok(templateService.moveCategory(request.getCategoryUid(), request.getNewParentUid()));
    }

    // ==================== ШАБЛОНЫ ====================

    @GetMapping
    public ResponseEntity<List<TemplateDto>> getAllTemplates(
            @RequestParam(required = false) Long categoryId) {
        if (categoryId != null) {
            return ResponseEntity.ok(templateService.getTemplatesByCategory(categoryId));
        }
        return ResponseEntity.ok(templateService.getAllTemplates());
    }

    @GetMapping("/{uid}")
    public ResponseEntity<TemplateDto> getTemplateById(@PathVariable UUID uid) {
        return ResponseEntity.ok(templateService.getTemplateById(uid));
    }

    @PostMapping
    public ResponseEntity<TemplateDto> createTemplate(@RequestBody TemplateRequest request) {
        return ResponseEntity.ok(templateService.createTemplate(request));
    }

    @PutMapping("/{uid}")
    public ResponseEntity<TemplateDto> updateTemplate(@PathVariable UUID uid, @RequestBody TemplateRequest request) {
        return ResponseEntity.ok(templateService.updateTemplate(uid, request));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID uid) {
        templateService.deleteTemplate(uid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/copy")
    public ResponseEntity<TemplateDto> copyTemplate(@RequestBody TemplateCopyRequest request) {
        return ResponseEntity.ok(templateService.copyTemplate(request));
    }

    @PostMapping("/move")
    public ResponseEntity<TemplateDto> moveTemplate(@RequestBody MoveTemplateRequest request) {
        return ResponseEntity.ok(templateService.moveTemplate(request.getTemplateUid(), request.getNewCategoryUid()));
    }

    @GetMapping("/{uid}/stations")
    public ResponseEntity<List<String>> getTemplateStations(@PathVariable UUID uid) {
        return ResponseEntity.ok(templateService.getTemplateStations(uid));
    }

    // ==================== ЯЧЕЙКИ ====================

    @GetMapping("/{uid}/cells")
    public ResponseEntity<List<CellDto>> getTemplateCells(@PathVariable UUID uid) {
        return ResponseEntity.ok(templateService.getTemplateCells(uid));
    }

    @PostMapping("/cells")
    public ResponseEntity<CellDto> createCell(@RequestBody CreateCellRequest request) {
        return ResponseEntity.ok(templateService.createCell(request));
    }

    @PutMapping("/cells/{uid}")
    public ResponseEntity<CellDto> updateCell(@PathVariable UUID uid, @RequestBody CellRequest request) {
        return ResponseEntity.ok(templateService.updateCell(uid, request));
    }

    @DeleteMapping("/cells/{uid}")
    public ResponseEntity<Void> clearCell(@PathVariable UUID uid) {
        templateService.clearCell(uid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cells/clear-batch")
    public ResponseEntity<Void> clearBatchCells(@RequestBody ClearBatchRequest request) {
        templateService.clearBatchCells(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{uid}/cells/batch-save")
    public ResponseEntity<Void> saveBatchCells(@PathVariable UUID uid,
                                               @RequestBody SaveBatchCellsRequest request) {
        templateService.saveBatchCells(uid, request);
        return ResponseEntity.noContent().build();
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
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"templates.xlsx\"")
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
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"templates.docx\"")
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
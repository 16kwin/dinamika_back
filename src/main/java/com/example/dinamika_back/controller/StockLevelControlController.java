package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.OfficeExportService;
import com.example.dinamika_back.service.PdfExportService;
import com.example.dinamika_back.service.StockLevelControlColumnSettingsService;
import com.example.dinamika_back.service.StockLevelControlCrudService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents/stock-level-control")
public class StockLevelControlController {

    private final StockLevelControlCrudService crudService;
    private final StockLevelControlColumnSettingsService columnSettingsService;
    private final PdfExportService pdfExportService;
    private final OfficeExportService officeExportService;

    @Autowired
    public StockLevelControlController(StockLevelControlCrudService crudService,
                                       StockLevelControlColumnSettingsService columnSettingsService,
                                       PdfExportService pdfExportService,
                                       OfficeExportService officeExportService) {
        this.crudService = crudService;
        this.columnSettingsService = columnSettingsService;
        this.pdfExportService = pdfExportService;
        this.officeExportService = officeExportService;
    }

    // ==================== CRUD ====================

    @GetMapping("/crud")
    public ResponseEntity<StockLevelControlListResponse> getAll(@RequestParam Integer userId) {
        return ResponseEntity.ok(crudService.getAll(userId));
    }

    @GetMapping("/crud/generate-code")
    public ResponseEntity<Integer> generateCode() {
        return ResponseEntity.ok(crudService.generateCode());
    }

    @GetMapping("/crud/{uid}")
    public ResponseEntity<StockLevelControlDto> getByUid(@PathVariable UUID uid) {
        return ResponseEntity.ok(crudService.getByUid(uid));
    }

    @PostMapping("/crud")
    public ResponseEntity<StockLevelControlDto> create(@RequestBody CreateStockLevelControlRequest request) {
        return ResponseEntity.ok(crudService.create(request));
    }

    @PatchMapping("/crud/{uid}")
    public ResponseEntity<StockLevelControlDto> update(@PathVariable UUID uid,
                                                       @RequestBody UpdateStockLevelControlRequest request) {
        return ResponseEntity.ok(crudService.update(uid, request));
    }

    @DeleteMapping("/crud/{uid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uid) {
        crudService.delete(uid);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/crud/{uid}/post")
    public ResponseEntity<StockLevelControlDto> post(@PathVariable UUID uid) {
        return ResponseEntity.ok(crudService.post(uid));
    }

    @PostMapping("/crud/{uid}/unpost")
    public ResponseEntity<StockLevelControlDto> unpost(@PathVariable UUID uid) {
        return ResponseEntity.ok(crudService.unpost(uid));
    }

    // ==================== REG ====================

    @GetMapping("/reg")
    public ResponseEntity<StockLevelControlRegDto> getReg(@RequestParam String stationUid,
                                                          @RequestParam UUID materialUid) {
        return ResponseEntity.ok(crudService.getRegByStationAndMaterial(stationUid, materialUid));
    }

    // ==================== EVENTS ====================

    @GetMapping("/crud/events")
    public ResponseEntity<List<StockLevelControlEventLogDto>> getAllEvents() {
        return ResponseEntity.ok(crudService.getAllEvents());
    }

    @GetMapping("/crud/{uid}/events")
    public ResponseEntity<List<StockLevelControlEventLogDto>> getEvents(@PathVariable UUID uid) {
        return ResponseEntity.ok(crudService.getEvents(uid));
    }

    // ==================== SETTINGS ====================

    @GetMapping("/settings")
    public ResponseEntity<Map<String, String>> getAllSettings(@RequestParam Integer userId) {
        Map<String, String> settings = Map.of(
                "columnsJson", columnSettingsService.getColumnsJson(userId) != null
                        ? columnSettingsService.getColumnsJson(userId) : "{}",
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
    public ResponseEntity<Void> saveSortSettings(@RequestParam Integer userId,
                                                 @RequestBody Map<String, Object> body) {
        String sortJson = (String) body.get("sortJson");
        columnSettingsService.saveSortJson(userId, sortJson);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/settings")
    public ResponseEntity<Void> saveAllSettings(@RequestParam Integer userId,
                                                @RequestBody Map<String, Object> body) {
        String columnsJson = (String) body.get("columnsJson");
        String filtersJson = (String) body.get("filtersJson");
        String sortJson = (String) body.get("sortJson");
        columnSettingsService.saveAllJson(userId, columnsJson, filtersJson, sortJson);
        return ResponseEntity.ok().build();
    }

    // ==================== EXPORT ====================

    @PostMapping("/crud/export-pdf")
    public ResponseEntity<byte[]> exportPdf(@RequestBody Map<String, Object> request) throws Exception {
        String title = (String) request.get("title");
        List<String> columns = (List<String>) request.get("columns");
        List<String> columnLabels = (List<String>) request.get("columnLabels");
        List<Map<String, Object>> data = (List<Map<String, Object>>) request.get("data");
        boolean landscape = (boolean) request.getOrDefault("landscape", false);
        List<String> footerLines = (List<String>) request.get("footerLines");

        byte[] pdf = pdfExportService.generatePdf(title, columns, columnLabels, data, landscape, footerLines);
        return buildPdfResponse(pdf, "stock-level-control.pdf", false);
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

    @PostMapping("/crud/export-excel")
    public ResponseEntity<byte[]> exportExcel(@RequestBody Map<String, Object> request) throws Exception {
        String title = (String) request.get("title");
        List<String> columns = (List<String>) request.get("columns");
        List<String> columnLabels = (List<String>) request.get("columnLabels");
        List<Map<String, Object>> data = (List<Map<String, Object>>) request.get("data");
        List<String> footerLines = (List<String>) request.get("footerLines");

        byte[] excel = officeExportService.exportExcel(title, columns, columnLabels, data, footerLines);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"stock-level-control.xlsx\"")
                .body(excel);
    }

    @PostMapping("/crud/export-word")
    public ResponseEntity<byte[]> exportWord(@RequestBody Map<String, Object> request) throws Exception {
        String title = (String) request.get("title");
        List<String> columns = (List<String>) request.get("columns");
        List<String> columnLabels = (List<String>) request.get("columnLabels");
        List<Map<String, Object>> data = (List<Map<String, Object>>) request.get("data");
        List<String> footerLines = (List<String>) request.get("footerLines");

        byte[] word = officeExportService.exportWord(title, columns, columnLabels, data, footerLines);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"stock-level-control.docx\"")
                .body(word);
    }
}
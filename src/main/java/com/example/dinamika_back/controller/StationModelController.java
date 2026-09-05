// StationModelController.java — ПОЛНЫЙ ФАЙЛ (добавлен renameDocument)
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.StationModelService;
import com.example.dinamika_back.service.OfficeExportService;
import com.example.dinamika_back.service.PdfExportService;
import com.example.dinamika_back.service.StationModelColumnSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/station-models")
@RequiredArgsConstructor
public class StationModelController {

    private final StationModelService stationModelService;
    private final StationModelColumnSettingsService columnSettingsService;
    private final PdfExportService pdfExportService;
    private final OfficeExportService officeExportService;

    // ==================== CRUD ====================

    @GetMapping
    public ResponseEntity<StationModelListResponse> getAll(@RequestParam(required = false) Integer userId) {
        return ResponseEntity.ok(stationModelService.getAllWithSettings(userId));
    }

    @GetMapping("/generate-code")
    public ResponseEntity<Map<String, Object>> generate() {
        UUID uid = UUID.randomUUID();
        Integer code = stationModelService.generateCode();
        Map<String, Object> result = new HashMap<>();
        result.put("uid", uid.toString());
        result.put("code", code);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{uid}")
    public ResponseEntity<StationModelDto> getById(@PathVariable UUID uid) {
        return ResponseEntity.ok(stationModelService.getById(uid));
    }

    @PostMapping
    public ResponseEntity<StationModelDto> create(@RequestBody CreateStationModelRequest request) {
        return ResponseEntity.ok(stationModelService.create(request));
    }

    @PatchMapping("/{uid}")
    public ResponseEntity<StationModelDto> update(@PathVariable UUID uid,
            @RequestBody UpdateStationModelRequest request) {
        return ResponseEntity.ok(stationModelService.update(uid, request));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uid) {
        stationModelService.delete(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== Images ====================

    @GetMapping("/{modelUid}/images")
    public ResponseEntity<List<StationModelImageDto>> getImages(@PathVariable UUID modelUid) {
        return ResponseEntity.ok(stationModelService.getImages(modelUid));
    }

    @PostMapping("/{modelUid}/images")
    public ResponseEntity<StationModelImageDto> uploadImage(@PathVariable UUID modelUid,
            @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(stationModelService.uploadImage(modelUid, file));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/images/{imageUid}")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID imageUid) {
        stationModelService.deleteImage(imageUid);
        return ResponseEntity.ok().build();
    }

    // ==================== Documents ====================

    @GetMapping("/{modelUid}/documents")
    public ResponseEntity<List<StationModelDocumentDto>> getDocuments(@PathVariable UUID modelUid) {
        return ResponseEntity.ok(stationModelService.getDocuments(modelUid));
    }

    @PostMapping("/{modelUid}/documents")
    public ResponseEntity<StationModelDocumentDto> uploadDocument(
            @PathVariable UUID modelUid,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentName") String documentName) {
        try {
            return ResponseEntity.ok(stationModelService.uploadDocument(modelUid, documentName, file));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PatchMapping("/documents/{documentUid}/rename")
    public ResponseEntity<StationModelDocumentDto> renameDocument(
            @PathVariable UUID documentUid,
            @RequestParam("documentName") String documentName) {
        return ResponseEntity.ok(stationModelService.renameDocument(documentUid, documentName));
    }

    @DeleteMapping("/documents/{documentUid}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID documentUid) {
        stationModelService.deleteDocument(documentUid);
        return ResponseEntity.ok().build();
    }

    // ==================== Events ====================

    @GetMapping("/events")
    public ResponseEntity<List<StationModelEventLogDto>> getAllEvents() {
        return ResponseEntity.ok(stationModelService.getAllEvents());
    }

    @GetMapping("/{uid}/events")
    public ResponseEntity<List<StationModelEventLogDto>> getEvents(@PathVariable UUID uid) {
        return ResponseEntity.ok(stationModelService.getEvents(uid));
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

    // ==================== ПДФ и ПЕЧАТЬ ====================

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
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"models.xlsx\"")
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
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"models.docx\"")
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
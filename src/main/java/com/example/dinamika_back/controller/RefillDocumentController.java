// RefillDocumentController.java
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.RefillColumnSettingsService;
import com.example.dinamika_back.service.RefillDocumentService;
import com.example.dinamika_back.service.StockRegisterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents/refill")
@RequiredArgsConstructor
public class RefillDocumentController {

    private final RefillDocumentService service;
    private final RefillColumnSettingsService columnSettingsService;
    private final StockRegisterService stockRegisterService;

    private String getAuthor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "Система";
    }

    // ==================== LIST ====================

    @GetMapping("/crud")
    public ResponseEntity<DocRefillListResponse> getAll(@RequestParam Integer userId) {
        return ResponseEntity.ok(service.getAll(userId));
    }

    // ==================== SNAPSHOT ====================

    @GetMapping("/snapshot")
    public ResponseEntity<DocRefillDto> getSnapshot(
            @RequestParam(required = false) String stationUid,
            @RequestParam(required = false) UUID templateUid) {
        return ResponseEntity.ok(service.buildSnapshot(stationUid, templateUid));
    }

    // ==================== GET BY UID ====================

    @GetMapping("/crud/{uid}")
    public ResponseEntity<DocRefillDto> getByUid(@PathVariable UUID uid) {
        return ResponseEntity.ok(service.getByUid(uid));
    }

    // ==================== GENERATE CODE ====================

    @GetMapping("/crud/generate-code")
    public ResponseEntity<Integer> generateCode() {
        return ResponseEntity.ok(service.generateCode());
    }

    // ==================== CREATE ====================

    @PostMapping("/crud")
    public ResponseEntity<DocRefillDto> create(@RequestBody CreateDocRefillRequest request) {
        return ResponseEntity.ok(service.create(request, getAuthor()));
    }

    // ==================== UPDATE ====================

    @PatchMapping("/crud/{uid}")
    public ResponseEntity<DocRefillDto> update(@PathVariable UUID uid,
                                               @RequestBody UpdateDocRefillRequest request) {
        return ResponseEntity.ok(service.update(uid, request, getAuthor()));
    }

    // ==================== DELETE ====================

    @DeleteMapping("/crud/{uid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uid) {
        service.delete(uid, getAuthor());
        return ResponseEntity.noContent().build();
    }

    // ==================== POST ====================

    @PostMapping("/crud/{uid}/post")
    public ResponseEntity<DocRefillDto> post(@PathVariable UUID uid) {
        return ResponseEntity.ok(service.post(uid, getAuthor()));
    }

    // ==================== EVENTS ====================

    @GetMapping("/crud/events")
    public ResponseEntity<List<RefillEventLogDto>> getAllEvents() {
        return ResponseEntity.ok(service.getAllEvents());
    }

    @GetMapping("/crud/{uid}/events")
    public ResponseEntity<List<RefillEventLogDto>> getEvents(@PathVariable UUID uid) {
        return ResponseEntity.ok(service.getEvents(uid));
    }

    // ==================== STOCK REGISTER ====================

    /** Детальные брони по конкретному документу пополнения. */
    @GetMapping("/crud/{uid}/stock/reservations")
    public ResponseEntity<List<StockReservationDto>> getReservations(@PathVariable UUID uid) {
        return ResponseEntity.ok(stockRegisterService.getReservationsByDoc(uid));
    }

    /**
     * Активные брони по номенклатуре на складе.
     * Используется фронтом в тултипе «Информация» — работает и в create-, и в edit-режиме.
     */
    @GetMapping("/stock/reservations")
    public ResponseEntity<List<StockReservationDto>> getReservationsByMaterial(
            @RequestParam UUID materialUid,
            @RequestParam(required = false) String stockUid) {
        return ResponseEntity.ok(stockRegisterService.getActiveReservationsByMaterial(materialUid, stockUid));
    }

    /** Журнал событий склада по конкретному документу пополнения. */
    @GetMapping("/crud/{uid}/stock/events")
    public ResponseEntity<List<StockEventLogDto>> getStockEvents(@PathVariable UUID uid) {
        return ResponseEntity.ok(stockRegisterService.getEventsByDoc(uid));
    }

    /** Сводный регистр остатков по складу (по умолчанию MAIN). */
    @GetMapping("/stock/balances")
    public ResponseEntity<List<StockBalanceDto>> getBalances(
            @RequestParam(required = false) String stockUid) {
        return ResponseEntity.ok(stockRegisterService.getBalances(stockUid));
    }

    /** Доступные (чистые) остатки по списку номенклатур. */
    @GetMapping("/stock/available")
    public ResponseEntity<Map<String, Integer>> getAvailable(
            @RequestParam List<UUID> materialUids,
            @RequestParam(required = false) String stockUid) {
        Map<UUID, Integer> map = stockRegisterService.getAvailableMap(materialUids, stockUid);
        Map<String, Integer> stringKeyed = new LinkedHashMap<>();
        map.forEach((k, v) -> stringKeyed.put(k.toString(), v));
        return ResponseEntity.ok(stringKeyed);
    }

    // ==================== SETTINGS ====================

    @GetMapping("/settings")
    public ResponseEntity<Map<String, String>> getSettings(@RequestParam Integer userId) {
        return ResponseEntity.ok(Map.of(
                "columnsJson", nullSafe(columnSettingsService.getColumnsJson(userId)),
                "filtersJson", nullSafe(columnSettingsService.getFiltersJson(userId)),
                "sortJson", nullSafe(columnSettingsService.getSortJson(userId))
        ));
    }

    @PatchMapping("/columns-settings")
    public ResponseEntity<Void> saveColumns(@RequestParam Integer userId,
                                            @RequestBody Map<String, String> body) {
        columnSettingsService.saveColumnsJson(userId, body.get("columnsJson"));
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/filters-settings")
    public ResponseEntity<Void> saveFilters(@RequestParam Integer userId,
                                            @RequestBody Map<String, String> body) {
        columnSettingsService.saveFiltersJson(userId, body.get("filtersJson"));
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/sort-settings")
    public ResponseEntity<Void> saveSort(@RequestParam Integer userId,
                                         @RequestBody Map<String, String> body) {
        columnSettingsService.saveSortJson(userId, body.get("sortJson"));
        return ResponseEntity.ok().build();
    }

    private String nullSafe(String s) {
        return s != null ? s : "{}";
    }
}
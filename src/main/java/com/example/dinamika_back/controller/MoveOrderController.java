// MoveOrderController.java
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.MoveOrderService;
import com.example.dinamika_back.service.MoveOrderColumnSettingsService;
import com.example.dinamika_back.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/move-orders")
@RequiredArgsConstructor
public class MoveOrderController {

    private final MoveOrderService moveOrderService;
    private final MoveOrderColumnSettingsService columnSettingsService;
    private final UserService userService;

    // ==================== CRUD ====================

    @GetMapping
    public ResponseEntity<MoveOrderListResponse> getAll(@RequestParam(required = false) Integer userId) {
        return ResponseEntity.ok(moveOrderService.getAllWithSettings(userId));
    }

    @GetMapping("/{uid}")
    public ResponseEntity<MoveOrderFlatDto> getByUid(@PathVariable UUID uid) {
        return ResponseEntity.ok(moveOrderService.getByUid(uid));
    }

    @GetMapping("/generate-code")
    public ResponseEntity<Integer> generateCode() {
        return ResponseEntity.ok(moveOrderService.generateCode());
    }

    @PostMapping
    public ResponseEntity<MoveOrderFlatDto> create(@RequestBody CreateMoveOrderRequest request) {
        return ResponseEntity.ok(moveOrderService.create(request, userService.getCurrentUsername()));
    }

    @PatchMapping("/{uid}")
    public ResponseEntity<MoveOrderFlatDto> update(@PathVariable UUID uid, @RequestBody UpdateMoveOrderRequest request) {
        return ResponseEntity.ok(moveOrderService.update(uid, request, userService.getCurrentUsername()));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uid) {
        moveOrderService.delete(uid, userService.getCurrentUsername());
        return ResponseEntity.ok().build();
    }

    // ==================== STATUS ACTIONS ====================

    /** DRAFT → ON_APPROVAL (только если useApproval = true). */
    @PostMapping("/{uid}/submit-approval")
    public ResponseEntity<MoveOrderFlatDto> submitForApproval(@PathVariable UUID uid) {
        return ResponseEntity.ok(moveOrderService.submitForApproval(uid, userService.getCurrentUsername()));
    }

    /** ON_APPROVAL → APPROVED, создаёт документы перемещения. */
    @PostMapping("/{uid}/approve")
    public ResponseEntity<MoveOrderFlatDto> approve(@PathVariable UUID uid) {
        return ResponseEntity.ok(moveOrderService.approve(uid, userService.getCurrentUsername()));
    }

    /** ON_APPROVAL → REJECTED. */
    @PostMapping("/{uid}/reject")
    public ResponseEntity<MoveOrderFlatDto> reject(@PathVariable UUID uid) {
        return ResponseEntity.ok(moveOrderService.reject(uid, userService.getCurrentUsername()));
    }

    /** DRAFT → IN_PROGRESS (когда useApproval = false), создаёт документы перемещения. */
    @PostMapping("/{uid}/take-to-work")
    public ResponseEntity<MoveOrderFlatDto> takeToWork(@PathVariable UUID uid) {
        return ResponseEntity.ok(moveOrderService.takeToWork(uid, userService.getCurrentUsername()));
    }

    /** DRAFT/ON_APPROVAL → CANCELLED. */
    @PostMapping("/{uid}/cancel")
    public ResponseEntity<MoveOrderFlatDto> cancel(@PathVariable UUID uid) {
        return ResponseEntity.ok(moveOrderService.cancel(uid, userService.getCurrentUsername()));
    }

    // ==================== Events ====================

    @GetMapping("/events")
    public ResponseEntity<List<MoveOrderEventLogDto>> getAllEvents() {
        return ResponseEntity.ok(moveOrderService.getAllEvents());
    }

    @GetMapping("/{uid}/events")
    public ResponseEntity<List<MoveOrderEventLogDto>> getEvents(@PathVariable UUID uid) {
        return ResponseEntity.ok(moveOrderService.getEvents(uid));
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
}
// AWMS — controller/TkpController.java — ПОЛНЫЙ ФАЙЛ
package com.example.dinamika_back.controller;

import com.example.dinamika_back.features.ConditionalOnFeature;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.service.TkpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ТКП (функция {@code orders}): без функции в потолке контроллера нет. Колбэки внешней системы (новое ТКП,
 * статус счёта) принимаются только с верным {@code X-API-Key} — проверяет OrdersCallbackKeyFilter.
 */
@RestController
@ConditionalOnFeature(Feature.ORDERS)
@RequestMapping("/api/tkp")
@RequiredArgsConstructor
public class TkpController {

    private final TkpService tkpService;

    @GetMapping("/active")
    public ResponseEntity<List<Map<String, Object>>> getActiveTkp() {
        return ResponseEntity.ok(tkpService.getActiveTkp());
    }

    @GetMapping("/closed")
    public ResponseEntity<List<Map<String, Object>>> getClosedTkp() {
        return ResponseEntity.ok(tkpService.getClosedTkp());
    }

    @GetMapping("/{tkpUid}")
    public ResponseEntity<Map<String, Object>> getTkp(@PathVariable String tkpUid) {
        return ResponseEntity.ok(tkpService.getTkp(tkpUid));
    }

    @PostMapping("/{tkpUid}")
    public ResponseEntity<Map<String, Object>> receiveTkp(
            @PathVariable String tkpUid,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.status(201).body(tkpService.receiveTkp(tkpUid, request));
    }

    @PostMapping("/{tkpUid}/confirm")
    public ResponseEntity<Map<String, Object>> confirmTkp(
            @PathVariable String tkpUid,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(tkpService.confirmTkp(tkpUid, request));
    }

    @PostMapping("/{tkpUid}/cancel")
    public ResponseEntity<Map<String, Object>> cancelTkp(@PathVariable String tkpUid) {
        return ResponseEntity.ok(tkpService.cancelTkp(tkpUid));
    }

    // Приём изменений от SAAS
    @PostMapping("/{tkpUid}/statusinvoice")
    public ResponseEntity<?> receiveTkpStatus(@PathVariable String tkpUid, @RequestBody Map<String, Object> request) {
        String statusinvoice = (String) request.get("statusinvoice");
        if (statusinvoice != null) {
            tkpService.receiveTkpStatusUpdate(tkpUid, statusinvoice);
        }
        return ResponseEntity.ok().build();
    }
}
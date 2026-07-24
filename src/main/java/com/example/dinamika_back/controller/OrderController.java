// AWMS — controller/OrderController.java — ПОЛНЫЙ ФАЙЛ (убрать addTrack для фронта)
package com.example.dinamika_back.controller;

import com.example.dinamika_back.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/active")
    public ResponseEntity<List<Map<String, Object>>> getActiveOrders() {
        return ResponseEntity.ok(orderService.getActiveOrders());
    }

    @GetMapping("/closed")
    public ResponseEntity<List<Map<String, Object>>> getClosedOrders() {
        return ResponseEntity.ok(orderService.getClosedOrders());
    }

    @GetMapping("/{orderUid}")
    public ResponseEntity<Map<String, Object>> getOrder(@PathVariable String orderUid) {
        return ResponseEntity.ok(orderService.getOrder(orderUid));
    }

    @PostMapping("/{orderUid}")
    public ResponseEntity<Map<String, Object>> createOrder(
            @PathVariable String orderUid,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(orderService.createOrder(orderUid, request));
    }

    @PostMapping("/{orderUid}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable String orderUid) {
        orderService.cancelOrder(orderUid);
        return ResponseEntity.ok().build();
    }

    // Приём трека от SAAS (проксирование из Задела)
    @PostMapping("/{orderUid}/track")
    public ResponseEntity<?> receiveTrack(@PathVariable String orderUid, @RequestBody Map<String, Object> request) {
        String statustrack = (String) request.get("statustrack");
        if (statustrack != null) {
            orderService.saveTrackLocally(orderUid, statustrack);
        }
        return ResponseEntity.ok().build();
    }

    // Приём изменений от SAAS
    @PostMapping("/{orderUid}/status")
    public ResponseEntity<?> receiveStatus(@PathVariable String orderUid, @RequestBody Map<String, Object> request) {
        String status = (String) request.get("status");
        if (status != null) {
            orderService.receiveStatusUpdate(orderUid, status);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{orderUid}/statusreason")
    public ResponseEntity<?> receiveStatusReason(@PathVariable String orderUid, @RequestBody Map<String, Object> request) {
        String statusreason = (String) request.get("statusreason");
        if (statusreason != null) {
            orderService.receiveStatusReasonUpdate(orderUid, statusreason);
        }
        return ResponseEntity.ok().build();
    }
}
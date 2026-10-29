package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.TransferDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferDocumentController {

    private final TransferDocumentService service;

    private String author(Authentication auth) {
        return auth != null ? auth.getName() : "system";
    }

    @GetMapping
    public TransferListResponse getAll(@RequestParam Integer userId) {
        return service.getAllWithSettings(userId);
    }

    @GetMapping("/{uid}")
    public TransferDetailDto getByUid(@PathVariable UUID uid) {
        return service.getByUid(uid);
    }

    @GetMapping("/generate-code")
    public Integer generateCode() {
        return service.generateCode();
    }

    @PostMapping
    public TransferDetailDto create(@RequestBody CreateTransferRequest request, Authentication auth) {
        return service.create(request, author(auth));
    }

    @PatchMapping("/{uid}")
    public TransferDetailDto update(@PathVariable UUID uid,
                                    @RequestBody UpdateTransferRequest request,
                                    Authentication auth) {
        return service.update(uid, request, author(auth));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uid, Authentication auth) {
        service.delete(uid, author(auth));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{uid}/agree")
    public TransferDetailDto agree(@PathVariable UUID uid, Authentication auth) {
        return service.agree(uid, author(auth));
    }

    @PostMapping("/{uid}/cancel")
    public TransferDetailDto cancel(@PathVariable UUID uid, Authentication auth) {
        return service.cancel(uid, author(auth));
    }

    @PostMapping("/{uid}/take-to-work")
    public TransferDetailDto takeToWork(@PathVariable UUID uid, Authentication auth) {
        return service.takeToWork(uid, author(auth));
    }

    @PostMapping("/{uid}/ship")
    public TransferDetailDto ship(@PathVariable UUID uid, Authentication auth) {
        return service.ship(uid, author(auth));
    }

    @PostMapping("/{uid}/receive")
    public TransferDetailDto receive(@PathVariable UUID uid, Authentication auth) {
        return service.receive(uid, author(auth));
    }

    @PostMapping("/{uid}/load")
    public TransferDetailDto load(@PathVariable UUID uid, Authentication auth) {
        return service.load(uid, author(auth));
    }

    @GetMapping("/{uid}/events")
    public List<TransferEventLogDto> getEvents(@PathVariable UUID uid) {
        return service.getEvents(uid);
    }

    @GetMapping("/events")
    public List<TransferEventLogDto> getAllEvents() {
        return service.getAllEvents();
    }
}
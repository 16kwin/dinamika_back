package com.example.dinamika_back.controller;

import com.example.dinamika_back.model.TestDocument;
import com.example.dinamika_back.service.TestDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncReceiverController {

    private final TestDocumentService testDocumentService;

    @PostMapping("/receive")
    public ResponseEntity<Map<String, Object>> receiveDocument(
            @RequestBody TestDocument document) {
        
        // Сохраняем в основную базу без повторной отправки во вторую
        TestDocument saved = testDocumentService.createWithoutSync(document);
        
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Document received in main database",
            "id", saved.getId()
        ));
    }
}
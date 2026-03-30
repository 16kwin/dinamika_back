// TestDocumentController.java
package com.example.dinamika_back.controller;

import com.example.dinamika_back.model.TestDocument;
import com.example.dinamika_back.service.TestDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/test-documents")
@RequiredArgsConstructor
public class TestDocumentController {

    private final TestDocumentService testDocumentService;

    @PostMapping
    public ResponseEntity<TestDocument> create(
            @RequestBody TestDocument document,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserIdFromUserDetails(userDetails);
        document.setUserId(userId);
        document.setCompleted(false);
        return ResponseEntity.ok(testDocumentService.create(document));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TestDocument> update(
            @PathVariable Long id,
            @RequestBody TestDocument document,
            @AuthenticationPrincipal UserDetails userDetails) {
        validateOwnership(id, userDetails);
        return ResponseEntity.ok(testDocumentService.update(id, document));
    }

    @GetMapping("/drafts")
    public ResponseEntity<List<TestDocument>> getDrafts(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserIdFromUserDetails(userDetails);
        return ResponseEntity.ok(testDocumentService.findDraftsByUserId(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TestDocument> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        validateOwnership(id, userDetails);
        return ResponseEntity.ok(testDocumentService.findById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        validateOwnership(id, userDetails);
        testDocumentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private Long getUserIdFromUserDetails(UserDetails userDetails) {
        // TODO: реализовать получение userId из userDetails
        return 1L;
    }

    private void validateOwnership(Long documentId, UserDetails userDetails) {
        TestDocument document = testDocumentService.findById(documentId);
        Long userId = getUserIdFromUserDetails(userDetails);
        if (!document.getUserId().equals(userId)) {
            throw new RuntimeException("Доступ запрещён");
        }
    }
}
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.StationDocumentDto;
import com.example.dinamika_back.service.StationDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stations/{stationUid}/documents")
@RequiredArgsConstructor
public class StationDocumentController {

    private final StationDocumentService documentService;

    @GetMapping
    public ResponseEntity<List<StationDocumentDto>> getDocuments(@PathVariable String stationUid) {
        return ResponseEntity.ok(documentService.getDocuments(stationUid));
    }

    @PostMapping
    public ResponseEntity<StationDocumentDto> uploadDocument(
            @PathVariable String stationUid,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentName") String documentName) {
        return ResponseEntity.ok(documentService.uploadDocument(stationUid, documentName, file));
    }

    @DeleteMapping("/{documentUid}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID documentUid) {
        documentService.deleteDocument(documentUid);
        return ResponseEntity.ok().build();
    }
}
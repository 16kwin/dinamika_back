package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.StationModelService;
import lombok.RequiredArgsConstructor;
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

    @GetMapping
    public ResponseEntity<List<StationModelDto>> getAll() {
        return ResponseEntity.ok(stationModelService.getAll());
    }

    @GetMapping("/generate")
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
    public ResponseEntity<StationModelDto> update(@PathVariable UUID uid, @RequestBody UpdateStationModelRequest request) {
        return ResponseEntity.ok(stationModelService.update(uid, request));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uid) {
        stationModelService.delete(uid);
        return ResponseEntity.ok().build();
    }

    // Изображения
    @GetMapping("/{modelUid}/images")
    public ResponseEntity<List<StationModelImageDto>> getImages(@PathVariable UUID modelUid) {
        return ResponseEntity.ok(stationModelService.getImages(modelUid));
    }

    @PostMapping("/{modelUid}/images")
    public ResponseEntity<StationModelImageDto> uploadImage(
            @PathVariable UUID modelUid,
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
}
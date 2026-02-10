package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.StationPositionDTO;
import com.example.dinamika_back.service.StationPositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/station-positions")
@RequiredArgsConstructor
public class StationPositionController {
    
    private final StationPositionService stationPositionService;
    
    // Создать или обновить позицию станции
    @PostMapping
    public ResponseEntity<?> createOrUpdatePosition(@RequestBody StationPositionDTO positionDTO) {
        try {
            StationPositionDTO savedPosition = stationPositionService.createOrUpdatePosition(positionDTO);
            return ResponseEntity.ok(savedPosition);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при сохранении позиции станции: " + e.getMessage());
        }
    }
    
    // Получить все позиции для конкретной локации
    @GetMapping("/location/{locationId}")
    public ResponseEntity<?> getPositionsByLocation(@PathVariable Integer locationId) {
        try {
            List<StationPositionDTO> positions = stationPositionService.getPositionsByLocation(locationId);
            return ResponseEntity.ok(positions);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при получении позиций станций: " + e.getMessage());
        }
    }
    
    // Получить все позиции для конкретной станции
    @GetMapping("/station/{stationId}")
    public ResponseEntity<?> getPositionsByStation(@PathVariable Integer stationId) {
        try {
            List<StationPositionDTO> positions = stationPositionService.getPositionsByStation(stationId);
            return ResponseEntity.ok(positions);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при получении позиций станции: " + e.getMessage());
        }
    }
    
    // Получить конкретную позицию
    @GetMapping("/{stationId}/{locationId}")
    public ResponseEntity<?> getPosition(
            @PathVariable Integer stationId,
            @PathVariable Integer locationId) {
        try {
            return stationPositionService.getPosition(stationId, locationId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при получении позиции: " + e.getMessage());
        }
    }
    
    // Удалить конкретную позицию
    @DeleteMapping("/{stationId}/{locationId}")
    public ResponseEntity<?> deletePosition(
            @PathVariable Integer stationId,
            @PathVariable Integer locationId) {
        try {
            stationPositionService.deletePosition(stationId, locationId);
            return ResponseEntity.ok("Позиция станции успешно удалена");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при удалении позиции: " + e.getMessage());
        }
    }
    
    // Удалить все позиции для станции
    @DeleteMapping("/station/{stationId}")
    public ResponseEntity<?> deleteAllPositionsByStation(@PathVariable Integer stationId) {
        try {
            stationPositionService.deleteAllPositionsByStation(stationId);
            return ResponseEntity.ok("Все позиции станции успешно удалены");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при удалении позиций станции: " + e.getMessage());
        }
    }
    
    // Удалить все позиции для локации
    @DeleteMapping("/location/{locationId}")
    public ResponseEntity<?> deleteAllPositionsByLocation(@PathVariable Integer locationId) {
        try {
            stationPositionService.deleteAllPositionsByLocation(locationId);
            return ResponseEntity.ok("Все позиции на локации успешно удалены");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при удалении позиций локации: " + e.getMessage());
        }
    }
}
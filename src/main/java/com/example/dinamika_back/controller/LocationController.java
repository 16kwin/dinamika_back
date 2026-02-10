package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.CreateLocationDTO;
import com.example.dinamika_back.dto.CreateStationDTO;
import com.example.dinamika_back.dto.LocationHierarchyDTO;
import com.example.dinamika_back.dto.LocationPhotoDTO;
import com.example.dinamika_back.model.Location;
import com.example.dinamika_back.model.Station;
import com.example.dinamika_back.service.LocationPhotoService;
import com.example.dinamika_back.service.LocationService;
import com.example.dinamika_back.service.MakeLocationService;
import com.example.dinamika_back.service.MakeStationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LocationController {
    
    private final LocationService locationService;
    private final MakeLocationService makeLocationService;
    private final MakeStationService makeStationService;
    private final LocationPhotoService locationPhotoService;
    
    @GetMapping("/locations/hierarchy")
    public ResponseEntity<List<LocationHierarchyDTO>> getLocationHierarchy() {
        List<LocationHierarchyDTO> hierarchy = locationService.getLocationHierarchy();
        return ResponseEntity.ok(hierarchy);
    }
    
    @GetMapping("/locations/hierarchy/first")
    public ResponseEntity<LocationHierarchyDTO> getFirstLocationHierarchy() {
        List<LocationHierarchyDTO> hierarchy = locationService.getLocationHierarchy();
        
        if (hierarchy.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(hierarchy.get(0));
    }
    
    @PostMapping("/locations")
    public ResponseEntity<?> createLocation(@RequestBody CreateLocationDTO dto) {
        try {
            Location location = makeLocationService.createLocation(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(location);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при создании локации: " + e.getMessage());
        }
    }
    
    @PostMapping("/stations")
    public ResponseEntity<?> createStation(@RequestBody CreateStationDTO dto) {
        try {
            Station station = makeStationService.createStation(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(station);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при создании станции: " + e.getMessage());
        }
    }
    
    // === НОВЫЕ МЕТОДЫ ДЛЯ ФОТО ===
    
    @PostMapping("/locations/{locationId}/photo")
    public ResponseEntity<?> uploadLocationPhoto(
            @PathVariable Integer locationId,
            @RequestParam("file") MultipartFile file) {
        try {
            LocationPhotoDTO photoDTO = locationPhotoService.uploadPhoto(locationId, file);
            return ResponseEntity.ok(photoDTO);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при загрузке фото: " + e.getMessage());
        }
    }

    @GetMapping("/locations/{locationId}/photo")
    public ResponseEntity<?> getLocationPhoto(@PathVariable Integer locationId) {
        try {
            LocationPhotoDTO photoDTO = locationPhotoService.getPhotoInfo(locationId);
            if (photoDTO == null) {
                return ResponseEntity.noContent().build(); // 204
            }
            return ResponseEntity.ok(photoDTO);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при получении информации о фото: " + e.getMessage());
        }
    }

    @DeleteMapping("/locations/{locationId}/photo")
    public ResponseEntity<?> deleteLocationPhoto(@PathVariable Integer locationId) {
        try {
            locationPhotoService.deletePhoto(locationId);
            return ResponseEntity.ok("Фото успешно удалено");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при удалении фото: " + e.getMessage());
        }
    }
}
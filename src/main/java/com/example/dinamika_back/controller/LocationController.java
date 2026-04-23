// LocationController.java
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.HierarchyDTO;
import com.example.dinamika_back.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;

    @Autowired
    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping("/hierarchy")
    public ResponseEntity<HierarchyDTO> getHierarchy() {
        HierarchyDTO hierarchy = locationService.getFullHierarchy();
        return ResponseEntity.ok(hierarchy);
    }
}
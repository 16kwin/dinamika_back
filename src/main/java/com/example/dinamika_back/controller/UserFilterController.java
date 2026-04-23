// UserFilterController.java
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.UserFilterDTO;
import com.example.dinamika_back.service.UserFilterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserFilterController {

    private final UserFilterService userFilterService;

    @Autowired
    public UserFilterController(UserFilterService userFilterService) {
        this.userFilterService = userFilterService;
    }

    @GetMapping("/filters")
    public ResponseEntity<UserFilterDTO> getUserFilters(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.ok(new UserFilterDTO());
        }
        
        String username = authentication.getName();
        UserFilterDTO filters = userFilterService.getUserFilters(username);
        return ResponseEntity.ok(filters);
    }

    @PostMapping("/filters")
    public ResponseEntity<?> saveUserFilters(@RequestBody UserFilterDTO filterDTO, 
                                             Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body("Not authenticated");
        }
        
        String username = authentication.getName();
        userFilterService.saveUserFilters(username, filterDTO);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/filters")
    public ResponseEntity<?> deleteUserFilters(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body("Not authenticated");
        }
        
        String username = authentication.getName();
        userFilterService.deleteUserFilters(username);
        return ResponseEntity.ok().build();
    }
}
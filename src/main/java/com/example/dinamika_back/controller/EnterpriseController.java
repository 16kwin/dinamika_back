// EnterpriseController.java
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.CreateEnterpriseRequest;
import com.example.dinamika_back.dto.EnterpriseFlatDto;
import com.example.dinamika_back.dto.UpdateEnterpriseRequest;
import com.example.dinamika_back.service.EnterpriseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/enterprises")
@RequiredArgsConstructor
public class EnterpriseController {

    private final EnterpriseService enterpriseService;

    @GetMapping
    public ResponseEntity<List<EnterpriseFlatDto>> getAll(@RequestParam(required = false) Long holdingId) {
        if (holdingId != null) {
            return ResponseEntity.ok(enterpriseService.getByHoldingId(holdingId));
        }
        return ResponseEntity.ok(enterpriseService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnterpriseFlatDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(enterpriseService.getById(id));
    }

    @PostMapping
    public ResponseEntity<EnterpriseFlatDto> create(@RequestBody CreateEnterpriseRequest request) {
        return ResponseEntity.ok(enterpriseService.create(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<EnterpriseFlatDto> update(@PathVariable Long id, @RequestBody UpdateEnterpriseRequest request) {
        return ResponseEntity.ok(enterpriseService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        enterpriseService.delete(id);
        return ResponseEntity.ok().build();
    }
}
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.CreateGroupRequest;
import com.example.dinamika_back.dto.GroupMaterialTreeDTO;
import com.example.dinamika_back.dto.NomenclatureCreateResponse;
import com.example.dinamika_back.dto.NomenclatureSaveRequest;
import com.example.dinamika_back.service.NomenclatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/nomenclature")
@RequiredArgsConstructor
public class NomenclatureController {

    private final NomenclatureService nomenclatureService;

    /**
     * GET /api/nomenclature/generate
     * Генерирует UUID и код для новой номенклатуры.
     */
    @GetMapping("/generate")
    public ResponseEntity<NomenclatureCreateResponse> generate() {
        return ResponseEntity.ok(nomenclatureService.generateCode());
    }

    /**
     * POST /api/nomenclature/draft
     * Сохраняет черновик номенклатуры.
     */
    @PostMapping("/draft")
    public ResponseEntity<Void> saveDraft(@RequestBody NomenclatureSaveRequest request) {
        nomenclatureService.saveDraft(request);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/nomenclature/tree
     * Возвращает полное дерево групп с вложенными материалами.
     */
    @GetMapping("/tree")
    public ResponseEntity<List<GroupMaterialTreeDTO>> getTree() {
        return ResponseEntity.ok(nomenclatureService.getFullTree());
    }

    /**
     * POST /api/nomenclature/groups
     * Создаёт новую группу материалов.
     */
    @PostMapping("/groups")
    public ResponseEntity<GroupMaterialTreeDTO> createGroup(@RequestBody CreateGroupRequest request) {
        return ResponseEntity.ok(nomenclatureService.createGroup(request));
    }
}
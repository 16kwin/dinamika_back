package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.NomenclatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/nomenclature")
@RequiredArgsConstructor
public class NomenclatureController {

    private final NomenclatureService nomenclatureService;

    // ==================== Генерация кода ====================

    @GetMapping("/generate")
    public ResponseEntity<NomenclatureCreateResponse> generate() {
        return ResponseEntity.ok(nomenclatureService.generateCode());
    }

    // ==================== Сохранение черновика ====================

    @PostMapping("/draft")
    public ResponseEntity<Void> saveDraft(@RequestBody NomenclatureSaveRequest request) {
        nomenclatureService.saveDraft(request);
        return ResponseEntity.ok().build();
    }

    // ==================== Получение одного материала ====================

    @GetMapping("/{uid}")
    public ResponseEntity<SprMaterialDTO> getMaterial(@PathVariable UUID uid) {
        return ResponseEntity.ok(nomenclatureService.getMaterial(uid));
    }

    // ==================== Дерево каталога ====================

    @GetMapping("/tree")
    public ResponseEntity<List<GroupMaterialTreeDTO>> getTree() {
        return ResponseEntity.ok(nomenclatureService.getFullTree());
    }

    // ==================== Группы каталога ====================

    @PostMapping("/groups")
    public ResponseEntity<GroupMaterialTreeDTO> createGroup(@RequestBody CreateGroupRequest request) {
        return ResponseEntity.ok(nomenclatureService.createGroup(request));
    }

    @PatchMapping("/groups/{uid}")
    public ResponseEntity<Void> renameGroup(@PathVariable UUID uid, @RequestBody RenameGroupRequest request) {
        nomenclatureService.renameGroup(uid, request.getName());
        return ResponseEntity.ok().build();
    }

    // ==================== Операции над элементами ====================

    @DeleteMapping("/items")
    public ResponseEntity<Void> deleteItems(@RequestBody BatchOperationRequest request) {
        nomenclatureService.deleteItems(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/items/copy")
    public ResponseEntity<Void> copyItems(@RequestBody BatchOperationRequest request) {
        nomenclatureService.copyItems(request);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/items/move")
    public ResponseEntity<Void> moveItems(@RequestBody BatchOperationRequest request) {
        nomenclatureService.moveItems(request);
        return ResponseEntity.ok().build();
    }

    // ==================== Группы учета ====================

    @GetMapping("/type-materials")
    public ResponseEntity<List<SprTypeMaterialDTO>> getTypeMaterials() {
        return ResponseEntity.ok(nomenclatureService.getTypeMaterials());
    }

    // ==================== Группы номенклатуры ====================

    /** Все группы номенклатуры (для справочника) */
    @GetMapping("/type-purposes")
    public ResponseEntity<List<SprTypePurposeDTO>> getTypePurposes(
            @RequestParam(required = false) UUID typeMaterialUid) {
        if (typeMaterialUid != null) {
            return ResponseEntity.ok(nomenclatureService.getTypePurposes(typeMaterialUid));
        }
        return ResponseEntity.ok(nomenclatureService.getAllTypePurposes());
    }

    /** Создать группу номенклатуры */
    @PostMapping("/type-purposes")
    public ResponseEntity<SprTypePurposeDTO> createTypePurpose(@RequestBody CreateTypePurposeRequest request) {
        return ResponseEntity.ok(nomenclatureService.createTypePurpose(request));
    }

    /** Обновить группу номенклатуры */
    @PatchMapping("/type-purposes/{uid}")
    public ResponseEntity<SprTypePurposeDTO> updateTypePurpose(
            @PathVariable UUID uid,
            @RequestBody UpdateTypePurposeRequest request) {
        return ResponseEntity.ok(nomenclatureService.updateTypePurpose(uid, request));
    }

    /** Удалить группу номенклатуры */
    @DeleteMapping("/type-purposes/{uid}")
    public ResponseEntity<Void> deleteTypePurpose(@PathVariable UUID uid) {
        nomenclatureService.deleteTypePurpose(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== Виды номенклатуры ====================

    /** Все виды номенклатуры (для справочника) */
    @GetMapping("/type-products")
    public ResponseEntity<List<SprTypeProductDTO>> getTypeProducts(
            @RequestParam(required = false) UUID typePurposeUid) {
        if (typePurposeUid != null) {
            return ResponseEntity.ok(nomenclatureService.getTypeProducts(typePurposeUid));
        }
        return ResponseEntity.ok(nomenclatureService.getAllTypeProducts());
    }

    /** Создать вид номенклатуры */
    @PostMapping("/type-products")
    public ResponseEntity<SprTypeProductDTO> createTypeProduct(@RequestBody CreateTypeProductRequest request) {
        return ResponseEntity.ok(nomenclatureService.createTypeProduct(request));
    }

    /** Обновить вид номенклатуры */
    @PatchMapping("/type-products/{uid}")
    public ResponseEntity<SprTypeProductDTO> updateTypeProduct(
            @PathVariable UUID uid,
            @RequestBody UpdateTypeProductRequest request) {
        return ResponseEntity.ok(nomenclatureService.updateTypeProduct(uid, request));
    }

    /** Удалить вид номенклатуры */
    @DeleteMapping("/type-products/{uid}")
    public ResponseEntity<Void> deleteTypeProduct(@PathVariable UUID uid) {
        nomenclatureService.deleteTypeProduct(uid);
        return ResponseEntity.ok().build();
    }
}
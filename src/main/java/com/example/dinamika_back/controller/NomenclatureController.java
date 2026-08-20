// NomenclatureController.java — ПОЛНЫЙ ФАЙЛ (с tree-with-settings)
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.NomenclatureColumnSettingsService;
import com.example.dinamika_back.service.NomenclatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/nomenclature")
@RequiredArgsConstructor
public class NomenclatureController {

    private final NomenclatureService nomenclatureService;
    private final NomenclatureColumnSettingsService columnSettingsService;

    // ==================== ДЕРЕВО С НАСТРОЙКАМИ ====================

    @GetMapping("/tree-with-settings")
    public ResponseEntity<NomenclatureTreeResponse> getTreeWithSettings(@RequestParam Integer userId) {
        return ResponseEntity.ok(nomenclatureService.getTreeWithSettings(userId));
    }

    // ==================== НАСТРОЙКИ КОЛОНОК, ФИЛЬТРОВ, СОРТИРОВКИ, ПУТИ ====================

    @GetMapping("/settings")
    public ResponseEntity<Map<String, String>> getAllSettings(@RequestParam Integer userId) {
        Map<String, String> settings = Map.of(
                "columnsJson", columnSettingsService.getColumnsJson(userId) != null
                        ? columnSettingsService.getColumnsJson(userId) : "{}",
                "filtersJson", columnSettingsService.getFiltersJson(userId),
                "sortJson", columnSettingsService.getSortJson(userId),
                "currentPathJson", columnSettingsService.getCurrentPathJson(userId)
        );
        return ResponseEntity.ok(settings);
    }

    @GetMapping("/columns-settings")
    public ResponseEntity<String> getColumnsSettings(@RequestParam Integer userId) {
        String json = columnSettingsService.getColumnsJson(userId);
        return ResponseEntity.ok(json != null ? json : "{}");
    }

    @PatchMapping("/columns-settings")
    public ResponseEntity<Void> saveColumnsSettings(@RequestParam Integer userId, @RequestBody Map<String, Object> body) {
        String columnsJson = (String) body.get("columnsJson");
        columnSettingsService.saveColumnsJson(userId, columnsJson);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/filters-settings")
    public ResponseEntity<String> getFiltersSettings(@RequestParam Integer userId) {
        return ResponseEntity.ok(columnSettingsService.getFiltersJson(userId));
    }

    @PatchMapping("/filters-settings")
    public ResponseEntity<Void> saveFiltersSettings(@RequestParam Integer userId, @RequestBody Map<String, Object> body) {
        String filtersJson = (String) body.get("filtersJson");
        columnSettingsService.saveFiltersJson(userId, filtersJson);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/sort-settings")
    public ResponseEntity<String> getSortSettings(@RequestParam Integer userId) {
        return ResponseEntity.ok(columnSettingsService.getSortJson(userId));
    }

    @PatchMapping("/sort-settings")
    public ResponseEntity<Void> saveSortSettings(@RequestParam Integer userId, @RequestBody Map<String, Object> body) {
        String sortJson = (String) body.get("sortJson");
        columnSettingsService.saveSortJson(userId, sortJson);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/current-path")
    public ResponseEntity<String> getCurrentPath(@RequestParam Integer userId) {
        return ResponseEntity.ok(columnSettingsService.getCurrentPathJson(userId));
    }

    @PatchMapping("/current-path")
    public ResponseEntity<Void> saveCurrentPath(@RequestParam Integer userId, @RequestBody Map<String, Object> body) {
        String currentPathJson = (String) body.get("currentPathJson");
        columnSettingsService.saveCurrentPathJson(userId, currentPathJson);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/settings")
    public ResponseEntity<Void> saveAllSettings(@RequestParam Integer userId, @RequestBody Map<String, Object> body) {
        String columnsJson = (String) body.get("columnsJson");
        String filtersJson = (String) body.get("filtersJson");
        String sortJson = (String) body.get("sortJson");
        String currentPathJson = (String) body.get("currentPathJson");
        columnSettingsService.saveAllJson(userId, columnsJson, filtersJson, sortJson, currentPathJson);
        return ResponseEntity.ok().build();
    }

    // ==================== Генерация кода ====================

    @GetMapping("/generate")
    public ResponseEntity<NomenclatureCreateResponse> generate() {
        return ResponseEntity.ok(nomenclatureService.generateCode());
    }

    // ==================== Сохранение черновика ====================

    @PostMapping("/draft")
    public ResponseEntity<Void> saveDraft(@RequestBody NomenclatureSaveRequest request) {
        String author = request.getAuthor() != null ? request.getAuthor() : "Система";
        nomenclatureService.saveDraft(request, author);
        return ResponseEntity.ok().build();
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

    @GetMapping("/type-purposes")
    public ResponseEntity<List<SprTypePurposeDTO>> getTypePurposes(
            @RequestParam(required = false) UUID typeMaterialUid) {
        if (typeMaterialUid != null) {
            return ResponseEntity.ok(nomenclatureService.getTypePurposes(typeMaterialUid));
        }
        return ResponseEntity.ok(nomenclatureService.getAllTypePurposes());
    }

    @PostMapping("/type-purposes")
    public ResponseEntity<SprTypePurposeDTO> createTypePurpose(@RequestBody CreateTypePurposeRequest request) {
        return ResponseEntity.ok(nomenclatureService.createTypePurpose(request));
    }

    @PatchMapping("/type-purposes/{uid}")
    public ResponseEntity<SprTypePurposeDTO> updateTypePurpose(
            @PathVariable UUID uid,
            @RequestBody UpdateTypePurposeRequest request) {
        return ResponseEntity.ok(nomenclatureService.updateTypePurpose(uid, request));
    }

    @DeleteMapping("/type-purposes/{uid}")
    public ResponseEntity<Void> deleteTypePurpose(@PathVariable UUID uid) {
        nomenclatureService.deleteTypePurpose(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== Виды номенклатуры ====================

    @GetMapping("/type-products")
    public ResponseEntity<List<SprTypeProductDTO>> getTypeProducts(
            @RequestParam(required = false) UUID typePurposeUid) {
        if (typePurposeUid != null) {
            return ResponseEntity.ok(nomenclatureService.getTypeProducts(typePurposeUid));
        }
        return ResponseEntity.ok(nomenclatureService.getAllTypeProducts());
    }

    @PostMapping("/type-products")
    public ResponseEntity<SprTypeProductDTO> createTypeProduct(@RequestBody CreateTypeProductRequest request) {
        return ResponseEntity.ok(nomenclatureService.createTypeProduct(request));
    }

    @PatchMapping("/type-products/{uid}")
    public ResponseEntity<SprTypeProductDTO> updateTypeProduct(
            @PathVariable UUID uid,
            @RequestBody UpdateTypeProductRequest request) {
        return ResponseEntity.ok(nomenclatureService.updateTypeProduct(uid, request));
    }

    @DeleteMapping("/type-products/{uid}")
    public ResponseEntity<Void> deleteTypeProduct(@PathVariable UUID uid) {
        nomenclatureService.deleteTypeProduct(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== Виды характеристик ====================

    @GetMapping("/type-attributes")
    public ResponseEntity<List<SprTypeAttributeDTO>> getTypeAttributes() {
        return ResponseEntity.ok(nomenclatureService.getTypeAttributes());
    }

    @PostMapping("/type-attributes")
    public ResponseEntity<SprTypeAttributeDTO> createTypeAttribute(@RequestBody CreateTypeAttributeRequest request) {
        return ResponseEntity.ok(nomenclatureService.createTypeAttribute(request));
    }

    @PatchMapping("/type-attributes/{uid}")
    public ResponseEntity<SprTypeAttributeDTO> updateTypeAttribute(
            @PathVariable UUID uid,
            @RequestBody UpdateTypeAttributeRequest request) {
        return ResponseEntity.ok(nomenclatureService.updateTypeAttribute(uid, request));
    }

    @DeleteMapping("/type-attributes/{uid}")
    public ResponseEntity<Void> deleteTypeAttribute(@PathVariable UUID uid) {
        nomenclatureService.deleteTypeAttribute(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== Единицы измерения ====================

    @GetMapping("/measures")
    public ResponseEntity<List<SprMeasureDTO>> getMeasures() {
        return ResponseEntity.ok(nomenclatureService.getMeasures());
    }

    @PostMapping("/measures")
    public ResponseEntity<SprMeasureDTO> createMeasure(@RequestBody CreateMeasureRequest request) {
        return ResponseEntity.ok(nomenclatureService.createMeasure(request));
    }

    @PatchMapping("/measures/{uid}")
    public ResponseEntity<SprMeasureDTO> updateMeasure(
            @PathVariable UUID uid,
            @RequestBody UpdateMeasureRequest request) {
        return ResponseEntity.ok(nomenclatureService.updateMeasure(uid, request));
    }

    @DeleteMapping("/measures/{uid}")
    public ResponseEntity<Void> deleteMeasure(@PathVariable UUID uid) {
        nomenclatureService.deleteMeasure(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== Производители ====================

    @GetMapping("/manufacturers")
    public ResponseEntity<List<SprManufacturerDTO>> getManufacturers() {
        return ResponseEntity.ok(nomenclatureService.getManufacturers());
    }

    @PostMapping("/manufacturers")
    public ResponseEntity<SprManufacturerDTO> createManufacturer(@RequestBody CreateManufacturerRequest request) {
        return ResponseEntity.ok(nomenclatureService.createManufacturer(request));
    }

    @PatchMapping("/manufacturers/{uid}")
    public ResponseEntity<SprManufacturerDTO> updateManufacturer(
            @PathVariable UUID uid,
            @RequestBody UpdateManufacturerRequest request) {
        return ResponseEntity.ok(nomenclatureService.updateManufacturer(uid, request));
    }

    @DeleteMapping("/manufacturers/{uid}")
    public ResponseEntity<Void> deleteManufacturer(@PathVariable UUID uid) {
        nomenclatureService.deleteManufacturer(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== Бренды ====================

    @GetMapping("/brands")
    public ResponseEntity<List<SprBrandDTO>> getBrands(
            @RequestParam(required = false) UUID manufacturerUid) {
        return ResponseEntity.ok(nomenclatureService.getBrands(manufacturerUid));
    }

    @PostMapping("/brands")
    public ResponseEntity<SprBrandDTO> createBrand(@RequestBody CreateBrandRequest request) {
        return ResponseEntity.ok(nomenclatureService.createBrand(request));
    }

    @PatchMapping("/brands/{uid}")
    public ResponseEntity<SprBrandDTO> updateBrand(
            @PathVariable UUID uid,
            @RequestBody UpdateBrandRequest request) {
        return ResponseEntity.ok(nomenclatureService.updateBrand(uid, request));
    }

    @DeleteMapping("/brands/{uid}")
    public ResponseEntity<Void> deleteBrand(@PathVariable UUID uid) {
        nomenclatureService.deleteBrand(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== Модели ====================

    @GetMapping("/models")
    public ResponseEntity<List<SprModelOfBrandDTO>> getModels(
            @RequestParam(required = false) UUID brandUid) {
        return ResponseEntity.ok(nomenclatureService.getModels(brandUid));
    }

    @PostMapping("/models")
    public ResponseEntity<SprModelOfBrandDTO> createModel(@RequestBody CreateModelRequest request) {
        return ResponseEntity.ok(nomenclatureService.createModel(request));
    }

    @PatchMapping("/models/{uid}")
    public ResponseEntity<SprModelOfBrandDTO> updateModel(
            @PathVariable UUID uid,
            @RequestBody UpdateModelRequest request) {
        return ResponseEntity.ok(nomenclatureService.updateModel(uid, request));
    }

    @DeleteMapping("/models/{uid}")
    public ResponseEntity<Void> deleteModel(@PathVariable UUID uid) {
        nomenclatureService.deleteModel(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== Страны ====================

    @GetMapping("/countries")
    public ResponseEntity<List<SprCountryDTO>> getCountries() {
        return ResponseEntity.ok(nomenclatureService.getCountries());
    }

    @PostMapping("/countries")
    public ResponseEntity<SprCountryDTO> createCountry(@RequestBody CreateCountryRequest request) {
        return ResponseEntity.ok(nomenclatureService.createCountry(request));
    }

    @PatchMapping("/countries/{uid}")
    public ResponseEntity<SprCountryDTO> updateCountry(
            @PathVariable UUID uid,
            @RequestBody UpdateCountryRequest request) {
        return ResponseEntity.ok(nomenclatureService.updateCountry(uid, request));
    }

    @DeleteMapping("/countries/{uid}")
    public ResponseEntity<Void> deleteCountry(@PathVariable UUID uid) {
        nomenclatureService.deleteCountry(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== ИЗОБРАЖЕНИЯ ====================

    @GetMapping("/{materialUid}/images")
    public ResponseEntity<List<MaterialMediaDTO>> getImages(@PathVariable UUID materialUid) {
        return ResponseEntity.ok(nomenclatureService.getImages(materialUid));
    }

    @PostMapping("/{materialUid}/images")
    public ResponseEntity<MaterialMediaDTO> uploadImage(
            @PathVariable UUID materialUid,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "author", defaultValue = "Система") String author) {
        try {
            return ResponseEntity.ok(nomenclatureService.uploadImage(materialUid, file, author));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/images/{uid}")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID uid,
                                            @RequestParam(value = "author", defaultValue = "Система") String author) {
        nomenclatureService.deleteImage(uid, author);
        return ResponseEntity.ok().build();
    }

    // ==================== ЧЕРТЕЖИ ====================

    @GetMapping("/{materialUid}/blueprints")
    public ResponseEntity<List<MaterialMediaDTO>> getBlueprints(@PathVariable UUID materialUid) {
        return ResponseEntity.ok(nomenclatureService.getBlueprints(materialUid));
    }

    @PostMapping("/{materialUid}/blueprints")
    public ResponseEntity<MaterialMediaDTO> uploadBlueprint(
            @PathVariable UUID materialUid,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "author", defaultValue = "Система") String author) {
        try {
            return ResponseEntity.ok(nomenclatureService.uploadBlueprint(materialUid, file, author));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/blueprints/{uid}")
    public ResponseEntity<Void> deleteBlueprint(@PathVariable UUID uid,
                                                @RequestParam(value = "author", defaultValue = "Система") String author) {
        nomenclatureService.deleteBlueprint(uid, author);
        return ResponseEntity.ok().build();
    }

    // ==================== КОДЫ (QR, BARCODE, SKU) ====================

    @GetMapping("/{materialUid}/codes")
    public ResponseEntity<List<MaterialCodeDTO>> getCodes(
            @PathVariable UUID materialUid,
            @RequestParam(value = "codeKind", required = false) String codeKind) {
        if (codeKind != null) {
            return ResponseEntity.ok(nomenclatureService.getCodesByKind(materialUid, codeKind));
        }
        return ResponseEntity.ok(nomenclatureService.getCodes(materialUid));
    }

    @PostMapping("/{materialUid}/codes")
    public ResponseEntity<MaterialCodeDTO> uploadCode(
            @PathVariable UUID materialUid,
            @RequestParam(value = "codeType", defaultValue = "QR_CODE") String codeType,
            @RequestParam(value = "codeValue", required = false) String codeValue,
            @RequestParam(value = "codeKind", defaultValue = "QR") String codeKind,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "author", defaultValue = "Система") String author) {
        try {
            return ResponseEntity.ok(nomenclatureService.uploadCode(materialUid, codeType, codeValue, codeKind, file, author));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/codes/{uid}")
    public ResponseEntity<Void> deleteCode(@PathVariable UUID uid,
                                           @RequestParam(value = "author", defaultValue = "Система") String author) {
        nomenclatureService.deleteCode(uid, author);
        return ResponseEntity.ok().build();
    }

    // ==================== ДОКУМЕНТЫ ====================

    @GetMapping("/{materialUid}/documents")
    public ResponseEntity<List<MaterialDocumentDTO>> getDocuments(@PathVariable UUID materialUid) {
        return ResponseEntity.ok(nomenclatureService.getDocuments(materialUid));
    }

    @PostMapping("/{materialUid}/documents")
    public ResponseEntity<MaterialDocumentDTO> uploadDocument(
            @PathVariable UUID materialUid,
            @RequestParam("documentName") String documentName,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "author", defaultValue = "Система") String author) {
        try {
            return ResponseEntity.ok(nomenclatureService.uploadDocument(materialUid, documentName, file, author));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/documents/{uid}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID uid,
                                               @RequestParam(value = "author", defaultValue = "Система") String author) {
        nomenclatureService.deleteDocument(uid, author);
        return ResponseEntity.ok().build();
    }

    // ==================== СПРАВОЧНИК ПОСТАВЩИКОВ ====================

    @GetMapping("/suppliers")
    public ResponseEntity<List<SprSupplierDTO>> getSuppliers() {
        return ResponseEntity.ok(nomenclatureService.getSuppliers());
    }

    @PostMapping("/suppliers")
    public ResponseEntity<SprSupplierDTO> createSupplier(@RequestBody CreateSupplierRequest request) {
        return ResponseEntity.ok(nomenclatureService.createSupplier(request));
    }

    @PatchMapping("/suppliers/{uid}")
    public ResponseEntity<SprSupplierDTO> updateSupplier(
            @PathVariable UUID uid,
            @RequestBody UpdateSupplierRequest request) {
        return ResponseEntity.ok(nomenclatureService.updateSupplier(uid, request));
    }

    @DeleteMapping("/suppliers/{uid}")
    public ResponseEntity<Void> deleteSupplier(@PathVariable UUID uid) {
        nomenclatureService.deleteSupplier(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== ПРИВЯЗКА ПОСТАВЩИКОВ К МАТЕРИАЛУ ====================

    @GetMapping("/{materialUid}/supply")
    public ResponseEntity<List<MaterialSupplyDTO>> getMaterialSupplies(@PathVariable UUID materialUid) {
        return ResponseEntity.ok(nomenclatureService.getMaterialSupplies(materialUid));
    }

    @PostMapping("/{materialUid}/supply")
    public ResponseEntity<MaterialSupplyDTO> addMaterialSupply(
            @PathVariable UUID materialUid,
            @RequestParam("supplierUid") UUID supplierUid,
            @RequestParam(value = "supplyDate", required = false) String supplyDate,
            @RequestParam(value = "documentName", required = false) String documentName,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "author", defaultValue = "Система") String author) {
        try {
            CreateSupplyRequest request = new CreateSupplyRequest();
            request.setSupplierUid(supplierUid);
            request.setSupplyDate(supplyDate != null ? LocalDateTime.parse(supplyDate) : null);
            request.setDocumentName(documentName);
            return ResponseEntity.ok(nomenclatureService.addMaterialSupply(materialUid, request, file, author));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/supply/{uid}")
    public ResponseEntity<Void> deleteMaterialSupply(@PathVariable UUID uid,
                                                     @RequestParam(value = "author", defaultValue = "Система") String author) {
        nomenclatureService.deleteMaterialSupply(uid, author);
        return ResponseEntity.ok().build();
    }

    // ==================== АНАЛОГИ ====================

    @GetMapping("/{materialUid}/analogs")
    public ResponseEntity<List<MaterialAnalogDTO>> getAnalogs(@PathVariable UUID materialUid) {
        return ResponseEntity.ok(nomenclatureService.getAnalogs(materialUid));
    }

    @PostMapping("/calculate-compatibility")
    public ResponseEntity<CalculateCompatibilityResponse> calculateCompatibility(
            @RequestBody CalculateCompatibilityRequest request) {
        return ResponseEntity.ok(nomenclatureService.calculateCompatibility(
                request.getMaterialUid1(), request.getMaterialUid2()));
    }

    @PostMapping("/{materialUid}/analogs")
    public ResponseEntity<MaterialAnalogDTO> addAnalog(
            @PathVariable UUID materialUid,
            @RequestBody CreateAnalogRequest request) {
        return ResponseEntity.ok(nomenclatureService.addAnalog(materialUid, request));
    }

    @DeleteMapping("/analogs/{uid}")
    public ResponseEntity<Void> deleteAnalog(@PathVariable UUID uid) {
        nomenclatureService.deleteAnalog(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== РЕЙТИНГ ====================

    @GetMapping("/{materialUid}/ratings")
    public ResponseEntity<List<MaterialRatingDTO>> getRatings(@PathVariable UUID materialUid) {
        return ResponseEntity.ok(nomenclatureService.getRatings(materialUid));
    }

    @GetMapping("/{materialUid}/ratings/average")
    public ResponseEntity<Double> getAverageRating(@PathVariable UUID materialUid) {
        return ResponseEntity.ok(nomenclatureService.getAverageRating(materialUid));
    }

    @PostMapping("/{materialUid}/ratings")
    public ResponseEntity<MaterialRatingDTO> addRating(
            @PathVariable UUID materialUid,
            @RequestBody AddRatingRequest request) {
        return ResponseEntity.ok(nomenclatureService.addRating(materialUid, request));
    }

    @DeleteMapping("/ratings/{uid}")
    public ResponseEntity<Void> deleteRating(@PathVariable UUID uid) {
        nomenclatureService.deleteRating(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== ИНТЕГРАЦИЯ ====================

    @GetMapping("/{materialUid}/integrations")
    public ResponseEntity<List<MaterialIntegrationDTO>> getIntegrations(@PathVariable UUID materialUid) {
        return ResponseEntity.ok(nomenclatureService.getIntegrations(materialUid));
    }

    @PostMapping("/{materialUid}/integrations")
    public ResponseEntity<MaterialIntegrationDTO> addIntegration(
            @PathVariable UUID materialUid,
            @RequestBody CreateIntegrationRequest request) {
        return ResponseEntity.ok(nomenclatureService.addIntegration(materialUid, request));
    }

    @DeleteMapping("/integrations/{uid}")
    public ResponseEntity<Void> deleteIntegration(@PathVariable UUID uid) {
        nomenclatureService.deleteIntegration(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== ЦЕНЫ ====================

    @GetMapping("/{materialUid}/prices")
    public ResponseEntity<List<MaterialPriceDTO>> getPrices(@PathVariable UUID materialUid) {
        return ResponseEntity.ok(nomenclatureService.getPrices(materialUid));
    }

    @PostMapping("/{materialUid}/prices")
    public ResponseEntity<MaterialPriceDTO> addPrice(
            @PathVariable UUID materialUid,
            @RequestBody AddPriceRequest request) {
        return ResponseEntity.ok(nomenclatureService.addPrice(materialUid, request));
    }

    @DeleteMapping("/prices/{priceUid}")
    public ResponseEntity<Void> deletePrice(@PathVariable UUID priceUid) {
        nomenclatureService.deletePrice(priceUid);
        return ResponseEntity.ok().build();
    }

    // ==================== ХАРАКТЕРИСТИКИ ====================

    @GetMapping("/{materialUid}/characteristics")
    public ResponseEntity<List<MaterialCharacteristicDTO>> getCharacteristics(@PathVariable UUID materialUid) {
        return ResponseEntity.ok(nomenclatureService.getCharacteristics(materialUid));
    }

    @PostMapping("/{materialUid}/characteristics")
    public ResponseEntity<MaterialCharacteristicDTO> addCharacteristic(
            @PathVariable UUID materialUid,
            @RequestBody CreateCharacteristicRequest request) {
        return ResponseEntity.ok(nomenclatureService.addCharacteristic(materialUid, request));
    }

    @PatchMapping("/characteristics/{uid}")
    public ResponseEntity<MaterialCharacteristicDTO> updateCharacteristic(
            @PathVariable UUID uid,
            @RequestBody UpdateCharacteristicRequest request) {
        return ResponseEntity.ok(nomenclatureService.updateCharacteristic(uid, request));
    }

    @DeleteMapping("/characteristics/{uid}")
    public ResponseEntity<Void> deleteCharacteristic(@PathVariable UUID uid) {
        nomenclatureService.deleteCharacteristic(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== ЖУРНАЛ СОБЫТИЙ ====================

    @GetMapping("/{materialUid}/events")
    public ResponseEntity<List<EventLogDTO>> getEvents(@PathVariable UUID materialUid) {
        return ResponseEntity.ok(nomenclatureService.getEvents(materialUid));
    }

    // ==================== Получение одного материала ====================

    @GetMapping("/{uid}")
    public ResponseEntity<SprMaterialDTO> getMaterial(@PathVariable UUID uid) {
        return ResponseEntity.ok(nomenclatureService.getMaterial(uid));
    }
}
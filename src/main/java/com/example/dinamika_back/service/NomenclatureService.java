// ==================== ПОЛНЫЙ NomenclatureService.java ====================
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.*;
import com.example.dinamika_back.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NomenclatureService {

    private final SprMaterialRepository materialRepository;
    private final RegGroupMaterialRepository groupMaterialRepository;
    private final SprTypeMaterialRepository typeMaterialRepository;
    private final SprTypePurposeRepository typePurposeRepository;
    private final SprTypeProductRepository typeProductRepository;
    private final SprMeasureRepository measureRepository;
    private final SprManufacturerRepository manufacturerRepository;
    private final SprBrandRepository brandRepository;
    private final SprModelOfBrandRepository modelOfBrandRepository;
    private final SprCountryRepository countryRepository;
    private final SprMaterialImageRepository imageRepository;
    private final SprMaterialBlueprintRepository blueprintRepository;
    private final SprMaterialCodeRepository codeRepository;
    private final SprMaterialDocumentRepository documentRepository;
    private final RegPriceRepository priceRepository;
    private final SprSupplierRepository sprSupplierRepository;
    private final DocEntranceRepository docEntranceRepository;
    private final RegAttributesRepository regAttributesRepository;
    private final SprTypeAttributesRepository typeAttributesRepository;
    private final RegSuppliersRepository regSuppliersRepository;
    private final RegAnalogRepository regAnalogRepository;
    private final RegRatingRepository regRatingRepository;
    private final RegIntegrationRepository regIntegrationRepository;

    private static final String NOMENCLATURE_UPLOAD_DIR = "uploads/nomenclature/";

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ ФАЙЛОВ ====================

    private Path getMaterialDir(UUID materialUid) throws IOException {
        Path dir = Path.of(NOMENCLATURE_UPLOAD_DIR, materialUid.toString());
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        return dir;
    }

    private String saveFile(UUID materialUid, MultipartFile file) throws IOException {
        Path dir = getMaterialDir(materialUid);
        String extension = getFileExtension(file.getOriginalFilename());
        String fileName = UUID.randomUUID().toString() + extension;
        Path filePath = dir.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        return fileName;
    }

    private void deleteFile(UUID materialUid, String fileName) {
        try {
            Path filePath = Path.of(NOMENCLATURE_UPLOAD_DIR, materialUid.toString(), fileName);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }

    private String getFileUrl(UUID materialUid, String filePath) {
        return "/uploads/nomenclature/" + materialUid + "/" + filePath;
    }

    // ==================== Получение материала ====================

    public SprMaterialDTO getMaterial(UUID uid) {
        SprMaterial material = materialRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Материал не найден: " + uid));

        SprMaterialDTO dto = new SprMaterialDTO();
        dto.setUid(material.getUid());
        dto.setCode(material.getCodeMaterial());
        dto.setName(material.getNameMaterial());
        dto.setArticle(material.getArticle());
        dto.setDescription(material.getDescription());
        dto.setUsage(material.getUsage());
        dto.setWasteMaterial(material.getWasteMaterial());
        dto.setRecycleMaterial(material.getRecycleMaterial());

        if (material.getGroupMaterial() != null) {
            dto.setGroupUid(material.getGroupMaterial().getUid());
            dto.setGroupName(material.getGroupMaterial().getGroupName());
        }

        if (material.getTypeMain() != null) {
            dto.setTypeMainUid(material.getTypeMain().getUid());
            dto.setTypeMainName(material.getTypeMain().getTypeName());
        }

        if (material.getTypePurpose() != null) {
            dto.setTypePurposeUid(material.getTypePurpose().getUid());
            dto.setTypePurposeName(material.getTypePurpose().getTypeName());
        }

        if (material.getTypeProduct() != null) {
            dto.setTypeProductUid(material.getTypeProduct().getUid());
            dto.setTypeProductName(material.getTypeProduct().getTypeName());
        }

        if (material.getMeasure() != null) {
            dto.setMeasureUid(material.getMeasure().getUid());
            dto.setMeasureName(material.getMeasure().getName());
        }

        if (material.getManufacturer() != null) {
            dto.setManufacturerUid(material.getManufacturer().getUid());
            dto.setManufacturerName(material.getManufacturer().getName());
        }

        if (material.getBrand() != null) {
            dto.setBrandUid(material.getBrand().getUid());
            dto.setBrandName(material.getBrand().getName());
        }

        if (material.getModelOfBrand() != null) {
            dto.setModelOfBrandUid(material.getModelOfBrand().getUid());
            dto.setModelOfBrandName(material.getModelOfBrand().getName());
        }

        if (material.getCountry() != null) {
            dto.setCountryUid(material.getCountry().getUid());
            dto.setCountryName(material.getCountry().getName());
        }

        return dto;
    }

    // ==================== Генерация кодов ====================

    public NomenclatureCreateResponse generateCode() {
        Integer maxCode = materialRepository.findMaxCodeMaterial();
        Integer code = maxCode + 1;
        return new NomenclatureCreateResponse(UUID.randomUUID(), code);
    }

    private Integer generateGroupCode() {
        Integer maxCode = groupMaterialRepository.findMaxGroupCode();
        return maxCode + 1;
    }

    private Integer generateMaterialCopyCode() {
        Integer maxCode = materialRepository.findMaxCodeMaterial();
        return maxCode + 1;
    }

    // ==================== Сохранение ====================

    @Transactional
    public void saveDraft(NomenclatureSaveRequest request) {
        SprMaterial material = materialRepository.findById(request.getUid())
                .orElseGet(() -> {
                    SprMaterial newMaterial = new SprMaterial();
                    newMaterial.setUid(request.getUid());
                    newMaterial.setCodeMaterial(request.getCode());
                    return newMaterial;
                });

        boolean isNewMaterial = material.getNameMaterial() == null;

        material.setNameMaterial(request.getName());
        material.setArticle(request.getArticle());
        material.setDescription(request.getDescription());
        material.setUsage(request.getUsage());
        material.setWasteMaterial(request.getWasteMaterial());
        material.setRecycleMaterial(request.getRecycleMaterial());

        if (request.getGroupUid() != null) {
            RegGroupMaterial group = groupMaterialRepository.findById(request.getGroupUid()).orElse(null);
            material.setGroupMaterial(group);
        }

        if (request.getTypeMainUid() != null) {
            material.setTypeMain(typeMaterialRepository.findById(request.getTypeMainUid()).orElse(null));
        }

        if (request.getTypePurposeUid() != null) {
            material.setTypePurpose(typePurposeRepository.findById(request.getTypePurposeUid()).orElse(null));
        }

        if (request.getTypeProductUid() != null) {
            material.setTypeProduct(typeProductRepository.findById(request.getTypeProductUid()).orElse(null));
        }

        if (request.getMeasureUid() != null) {
            material.setMeasure(measureRepository.findById(request.getMeasureUid()).orElse(null));
        }

        if (request.getManufacturerUid() != null) {
            material.setManufacturer(manufacturerRepository.findById(request.getManufacturerUid()).orElse(null));
        }

        if (request.getBrandUid() != null) {
            material.setBrand(brandRepository.findById(request.getBrandUid()).orElse(null));
        }

        if (request.getModelOfBrandUid() != null) {
            material.setModelOfBrand(modelOfBrandRepository.findById(request.getModelOfBrandUid()).orElse(null));
        }

        if (request.getCountryUid() != null) {
            material.setCountry(countryRepository.findById(request.getCountryUid()).orElse(null));
        }

        materialRepository.save(material);

        if (isNewMaterial) {
            createDefaultCharacteristics(material.getUid());
        }
    }

    // ==================== ХАРАКТЕРИСТИКИ ====================

    private void createDefaultCharacteristics(UUID materialUid) {
        SprMaterial material = materialRepository.findById(materialUid)
                .orElseThrow(() -> new RuntimeException("Материал не найден: " + materialUid));

        String[] defaultNames = {"Длина", "Ширина", "Высота", "Масса"};
        
        for (String name : defaultNames) {
            SprTypeAttributes attrType = typeAttributesRepository.findAll().stream()
                    .filter(a -> name.equals(a.getName()))
                    .findFirst()
                    .orElseGet(() -> {
                        SprTypeAttributes newAttr = new SprTypeAttributes();
                        newAttr.setUid(UUID.randomUUID());
                        newAttr.setName(name);
                        return typeAttributesRepository.save(newAttr);
                    });

            RegAttributes regAttr = new RegAttributes();
            regAttr.setUid(UUID.randomUUID());
            regAttr.setMaterial(material);
            regAttr.setAttributeType(attrType);
            regAttr.setMeaning(null);
            regAttr.setMeasure(null);
            regAttributesRepository.save(regAttr);
        }
    }

    public List<MaterialCharacteristicDTO> getCharacteristics(UUID materialUid) {
        List<RegAttributes> attrs = regAttributesRepository.findByMaterialUid(materialUid);
        
        return attrs.stream()
                .map(a -> {
                    boolean isCustom = a.getAttributeType() == null;
                    return new MaterialCharacteristicDTO(
                            a.getUid(),
                            a.getMaterial() != null ? a.getMaterial().getUid() : materialUid,
                            a.getAttributeType() != null ? a.getAttributeType().getUid() : null,
                            a.getAttributeType() != null ? a.getAttributeType().getName() : null,
                            isCustom ? (a.getMeaning() != null ? a.getMeaning() : "Пользовательская") : null,
                            isCustom ? null : a.getMeaning(),
                            a.getMeasure() != null ? a.getMeasure().getUid() : null,
                            a.getMeasure() != null ? a.getMeasure().getName() : null,
                            isCustom
                    );
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public MaterialCharacteristicDTO addCharacteristic(UUID materialUid, CreateCharacteristicRequest request) {
        SprMaterial material = materialRepository.findById(materialUid)
                .orElseThrow(() -> new RuntimeException("Материал не найден: " + materialUid));

        RegAttributes regAttr = new RegAttributes();
        regAttr.setUid(UUID.randomUUID());
        regAttr.setMaterial(material);

        if (request.getAttributeTypeUid() != null) {
            SprTypeAttributes attrType = typeAttributesRepository.findById(request.getAttributeTypeUid())
                    .orElseThrow(() -> new RuntimeException("Тип атрибута не найден: " + request.getAttributeTypeUid()));
            regAttr.setAttributeType(attrType);
            regAttr.setMeaning(request.getValue());
        } else {
            regAttr.setAttributeType(null);
            regAttr.setMeaning(request.getCustomName() + "::" + (request.getValue() != null ? request.getValue() : ""));
        }

        if (request.getMeasureUid() != null) {
            regAttr.setMeasure(measureRepository.findById(request.getMeasureUid()).orElse(null));
        }

        regAttributesRepository.save(regAttr);

        return new MaterialCharacteristicDTO(
                regAttr.getUid(),
                materialUid,
                regAttr.getAttributeType() != null ? regAttr.getAttributeType().getUid() : null,
                regAttr.getAttributeType() != null ? regAttr.getAttributeType().getName() : null,
                request.getCustomName(),
                request.getValue(),
                regAttr.getMeasure() != null ? regAttr.getMeasure().getUid() : null,
                regAttr.getMeasure() != null ? regAttr.getMeasure().getName() : null,
                request.getAttributeTypeUid() == null
        );
    }

    @Transactional
    public MaterialCharacteristicDTO updateCharacteristic(UUID characteristicUid, UpdateCharacteristicRequest request) {
        RegAttributes attr = regAttributesRepository.findById(characteristicUid)
                .orElseThrow(() -> new RuntimeException("Характеристика не найдена: " + characteristicUid));

        if (request.getValue() != null) {
            if (attr.getAttributeType() == null && attr.getMeaning() != null && attr.getMeaning().contains("::")) {
                String customName = attr.getMeaning().split("::")[0];
                attr.setMeaning(customName + "::" + request.getValue());
            } else {
                attr.setMeaning(request.getValue());
            }
        }

        if (request.getMeasureUid() != null) {
            attr.setMeasure(measureRepository.findById(request.getMeasureUid()).orElse(null));
        }

        regAttributesRepository.save(attr);

        return new MaterialCharacteristicDTO(
                attr.getUid(),
                attr.getMaterial() != null ? attr.getMaterial().getUid() : null,
                attr.getAttributeType() != null ? attr.getAttributeType().getUid() : null,
                attr.getAttributeType() != null ? attr.getAttributeType().getName() : null,
                attr.getAttributeType() == null && attr.getMeaning() != null && attr.getMeaning().contains("::") 
                    ? attr.getMeaning().split("::")[0] : null,
                attr.getAttributeType() != null ? attr.getMeaning() : 
                    (attr.getMeaning() != null && attr.getMeaning().contains("::") ? attr.getMeaning().split("::")[1] : attr.getMeaning()),
                attr.getMeasure() != null ? attr.getMeasure().getUid() : null,
                attr.getMeasure() != null ? attr.getMeasure().getName() : null,
                attr.getAttributeType() == null
        );
    }

    @Transactional
    public void deleteCharacteristic(UUID characteristicUid) {
        regAttributesRepository.deleteById(characteristicUid);
    }

    // ==================== ВИДЫ ХАРАКТЕРИСТИК ====================

    public List<SprTypeAttributeDTO> getTypeAttributes() {
        return typeAttributesRepository.findAll().stream()
                .map(a -> new SprTypeAttributeDTO(a.getUid(), a.getName(), a.getDesignation()))
                .collect(Collectors.toList());
    }

    @Transactional
    public SprTypeAttributeDTO createTypeAttribute(CreateTypeAttributeRequest request) {
        SprTypeAttributes attr = new SprTypeAttributes();
        attr.setUid(UUID.randomUUID());
        attr.setName(request.getName());
        attr.setDesignation(request.getDesignation());
        typeAttributesRepository.save(attr);
        return new SprTypeAttributeDTO(attr.getUid(), attr.getName(), attr.getDesignation());
    }

    @Transactional
    public SprTypeAttributeDTO updateTypeAttribute(UUID uid, UpdateTypeAttributeRequest request) {
        SprTypeAttributes attr = typeAttributesRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Вид характеристики не найден: " + uid));
        if (request.getName() != null) attr.setName(request.getName());
        if (request.getDesignation() != null) attr.setDesignation(request.getDesignation());
        typeAttributesRepository.save(attr);
        return new SprTypeAttributeDTO(attr.getUid(), attr.getName(), attr.getDesignation());
    }

    @Transactional
    public void deleteTypeAttribute(UUID uid) {
        typeAttributesRepository.deleteById(uid);
    }

    // ==================== Дерево ====================

    public List<GroupMaterialTreeDTO> getFullTree() {
        List<RegGroupMaterial> allGroups = groupMaterialRepository.findAll();

        List<RegGroupMaterial> roots = allGroups.stream()
                .filter(g -> g.getParentGroup() == null)
                .collect(Collectors.toList());

        return roots.stream()
                .map(root -> buildFullTree(root, allGroups))
                .collect(Collectors.toList());
    }

    private GroupMaterialTreeDTO buildFullTree(RegGroupMaterial group, List<RegGroupMaterial> allGroups) {
        GroupMaterialTreeDTO dto = new GroupMaterialTreeDTO();
        dto.setUid(group.getUid());
        dto.setName(group.getGroupName());
        dto.setCode(group.getGroupCode());

        List<GroupMaterialTreeDTO> children = allGroups.stream()
                .filter(g -> g.getParentGroup() != null && g.getParentGroup().equals(group.getUid()))
                .map(child -> buildFullTree(child, allGroups))
                .collect(Collectors.toList());
        dto.setChildren(children);

        List<SprMaterial> materials = materialRepository.findByGroupMaterialUid(group.getUid());
        List<MaterialItemDTO> materialItems = materials.stream()
                .map(m -> {
                    MaterialItemDTO item = new MaterialItemDTO();
                    item.setUid(m.getUid());
                    item.setName(m.getNameMaterial());
                    item.setArticle(m.getArticle());
                    item.setCode(m.getCodeMaterial());
                    item.setTypeMainName(m.getTypeMain() != null ? m.getTypeMain().getTypeName() : null);
                    item.setTypePurposeName(m.getTypePurpose() != null ? m.getTypePurpose().getTypeName() : null);
                    item.setTypeProductName(m.getTypeProduct() != null ? m.getTypeProduct().getTypeName() : null);
                    return item;
                })
                .collect(Collectors.toList());
        dto.setMaterials(materialItems);

        return dto;
    }

    // ==================== Создание группы ====================

    @Transactional
    public GroupMaterialTreeDTO createGroup(CreateGroupRequest request) {
        RegGroupMaterial group = new RegGroupMaterial();
        group.setUid(UUID.randomUUID());
        group.setGroupName(request.getName());
        group.setParentGroup(request.getParentUid());
        group.setGroupCode(generateGroupCode());

        groupMaterialRepository.save(group);

        GroupMaterialTreeDTO dto = new GroupMaterialTreeDTO();
        dto.setUid(group.getUid());
        dto.setName(group.getGroupName());
        dto.setCode(group.getGroupCode());
        dto.setChildren(new ArrayList<>());
        dto.setMaterials(new ArrayList<>());
        return dto;
    }

    // ==================== Переименование группы ====================

    @Transactional
    public void renameGroup(UUID uid, String newName) {
        RegGroupMaterial group = groupMaterialRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Группа не найдена: " + uid));
        group.setGroupName(newName);
        groupMaterialRepository.save(group);
    }

    // ==================== Удаление ====================

    @Transactional
    public void deleteItems(BatchOperationRequest request) {
        if (request.getMaterialUids() != null) {
            for (UUID materialUid : request.getMaterialUids()) {
                deleteAllMaterialMedia(materialUid);
                regAttributesRepository.deleteByMaterialUid(materialUid);
            }
            materialRepository.deleteAllById(request.getMaterialUids());
        }

        if (request.getGroupUids() != null) {
            for (UUID groupUid : request.getGroupUids()) {
                deleteGroupRecursive(groupUid);
            }
        }
    }

    private void deleteGroupRecursive(UUID groupUid) {
        RegGroupMaterial group = groupMaterialRepository.findById(groupUid).orElse(null);
        if (group == null) return;

        List<RegGroupMaterial> children = groupMaterialRepository.findByParentGroup(groupUid);
        for (RegGroupMaterial child : children) {
            deleteGroupRecursive(child.getUid());
        }

        List<SprMaterial> materials = materialRepository.findByGroupMaterialUid(groupUid);
        for (SprMaterial material : materials) {
            deleteAllMaterialMedia(material.getUid());
            regAttributesRepository.deleteByMaterialUid(material.getUid());
        }
        materialRepository.deleteAll(materials);

        groupMaterialRepository.delete(group);
    }

    // ==================== Копирование ====================

    @Transactional
    public void copyItems(BatchOperationRequest request) {
        Map<UUID, UUID> groupUidMap = new HashMap<>();

        if (request.getGroupUids() != null) {
            for (UUID groupUid : request.getGroupUids()) {
                copyGroupRecursive(groupUid, request.getTargetParentUid(), groupUidMap);
            }
        }

        if (request.getMaterialUids() != null) {
            for (UUID materialUid : request.getMaterialUids()) {
                copyMaterial(materialUid, request.getTargetParentUid());
            }
        }
    }

    private UUID copyGroupRecursive(UUID sourceGroupUid, UUID targetParentUid, Map<UUID, UUID> uidMap) {
        RegGroupMaterial source = groupMaterialRepository.findById(sourceGroupUid).orElse(null);
        if (source == null) return null;

        RegGroupMaterial copy = new RegGroupMaterial();
        copy.setUid(UUID.randomUUID());
        copy.setGroupName(source.getGroupName());
        copy.setParentGroup(targetParentUid);
        copy.setGroupCode(generateGroupCode());
        groupMaterialRepository.save(copy);

        uidMap.put(sourceGroupUid, copy.getUid());

        List<SprMaterial> materials = materialRepository.findByGroupMaterialUid(sourceGroupUid);
        for (SprMaterial material : materials) {
            copyMaterialToGroup(material.getUid(), copy.getUid());
        }

        List<RegGroupMaterial> children = groupMaterialRepository.findByParentGroup(sourceGroupUid);
        for (RegGroupMaterial child : children) {
            copyGroupRecursive(child.getUid(), copy.getUid(), uidMap);
        }

        return copy.getUid();
    }

    private void copyMaterial(UUID materialUid, UUID targetGroupUid) {
        SprMaterial source = materialRepository.findById(materialUid).orElse(null);
        if (source == null) return;
        copyMaterialToGroup(materialUid, targetGroupUid);
    }

    private void copyMaterialToGroup(UUID materialUid, UUID targetGroupUid) {
        SprMaterial source = materialRepository.findById(materialUid).orElse(null);
        if (source == null) return;

        SprMaterial copy = new SprMaterial();
        copy.setUid(UUID.randomUUID());
        copy.setCodeMaterial(generateMaterialCopyCode());
        copy.setNameMaterial(source.getNameMaterial());
        copy.setArticle(source.getArticle());
        copy.setDescription(source.getDescription());
        copy.setUsage(source.getUsage());
        copy.setWasteMaterial(source.getWasteMaterial());
        copy.setRecycleMaterial(source.getRecycleMaterial());
        copy.setMeasure(source.getMeasure());
        copy.setManufacturer(source.getManufacturer());
        copy.setBrand(source.getBrand());
        copy.setModelOfBrand(source.getModelOfBrand());
        copy.setCountry(source.getCountry());

        if (targetGroupUid != null) {
            RegGroupMaterial group = groupMaterialRepository.findById(targetGroupUid).orElse(null);
            copy.setGroupMaterial(group);
        }

        materialRepository.save(copy);
    }

    // ==================== Перемещение ====================

    @Transactional
    public void moveItems(BatchOperationRequest request) {
        if (request.getGroupUids() != null) {
            for (UUID groupUid : request.getGroupUids()) {
                RegGroupMaterial group = groupMaterialRepository.findById(groupUid).orElse(null);
                if (group != null) {
                    group.setParentGroup(request.getTargetParentUid());
                    groupMaterialRepository.save(group);
                }
            }
        }

        if (request.getMaterialUids() != null) {
            RegGroupMaterial targetGroup = request.getTargetParentUid() != null
                    ? groupMaterialRepository.findById(request.getTargetParentUid()).orElse(null)
                    : null;

            for (UUID materialUid : request.getMaterialUids()) {
                SprMaterial material = materialRepository.findById(materialUid).orElse(null);
                if (material != null) {
                    material.setGroupMaterial(targetGroup);
                    materialRepository.save(material);
                }
            }
        }
    }

    // ==================== СПРАВОЧНИКИ: группы учета ====================

    public List<SprTypeMaterialDTO> getTypeMaterials() {
        return typeMaterialRepository.findAll().stream()
                .map(m -> new SprTypeMaterialDTO(m.getUid(), m.getTypeName()))
                .collect(Collectors.toList());
    }

    // ==================== СПРАВОЧНИКИ: группы номенклатуры ====================

    public List<SprTypePurposeDTO> getAllTypePurposes() {
        return typePurposeRepository.findAll().stream()
                .map(p -> new SprTypePurposeDTO(
                        p.getUid(), p.getTypeName(),
                        p.getTypeMaterial() != null ? p.getTypeMaterial().getUid() : null,
                        p.getTypeMaterial() != null ? p.getTypeMaterial().getTypeName() : null))
                .collect(Collectors.toList());
    }

    public List<SprTypePurposeDTO> getTypePurposes(UUID typeMaterialUid) {
        return typePurposeRepository.findByTypeMaterialUid(typeMaterialUid).stream()
                .map(p -> new SprTypePurposeDTO(
                        p.getUid(), p.getTypeName(),
                        p.getTypeMaterial() != null ? p.getTypeMaterial().getUid() : null,
                        p.getTypeMaterial() != null ? p.getTypeMaterial().getTypeName() : null))
                .collect(Collectors.toList());
    }

    @Transactional
    public SprTypePurposeDTO createTypePurpose(CreateTypePurposeRequest request) {
        SprTypePurpose purpose = new SprTypePurpose();
        purpose.setUid(UUID.randomUUID());
        purpose.setTypeName(request.getName());
        if (request.getTypeMaterialUid() != null) {
            purpose.setTypeMaterial(typeMaterialRepository.findById(request.getTypeMaterialUid()).orElse(null));
        }
        typePurposeRepository.save(purpose);
        return new SprTypePurposeDTO(
                purpose.getUid(), purpose.getTypeName(),
                purpose.getTypeMaterial() != null ? purpose.getTypeMaterial().getUid() : null,
                purpose.getTypeMaterial() != null ? purpose.getTypeMaterial().getTypeName() : null);
    }

    @Transactional
    public SprTypePurposeDTO updateTypePurpose(UUID uid, UpdateTypePurposeRequest request) {
        SprTypePurpose purpose = typePurposeRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Группа номенклатуры не найдена: " + uid));
        if (request.getName() != null) purpose.setTypeName(request.getName());
        if (request.getTypeMaterialUid() != null) {
            purpose.setTypeMaterial(typeMaterialRepository.findById(request.getTypeMaterialUid()).orElse(null));
        }
        typePurposeRepository.save(purpose);
        return new SprTypePurposeDTO(
                purpose.getUid(), purpose.getTypeName(),
                purpose.getTypeMaterial() != null ? purpose.getTypeMaterial().getUid() : null,
                purpose.getTypeMaterial() != null ? purpose.getTypeMaterial().getTypeName() : null);
    }

    @Transactional
    public void deleteTypePurpose(UUID uid) {
        typePurposeRepository.deleteById(uid);
    }

    // ==================== СПРАВОЧНИКИ: виды номенклатуры ====================

    public List<SprTypeProductDTO> getAllTypeProducts() {
        return typeProductRepository.findAll().stream()
                .map(p -> new SprTypeProductDTO(
                        p.getUid(), p.getTypeName(),
                        p.getTypePurpose() != null ? p.getTypePurpose().getUid() : null,
                        p.getTypePurpose() != null ? p.getTypePurpose().getTypeName() : null,
                        p.getTypePurpose() != null && p.getTypePurpose().getTypeMaterial() != null
                                ? p.getTypePurpose().getTypeMaterial().getTypeName() : null))
                .collect(Collectors.toList());
    }

    public List<SprTypeProductDTO> getTypeProducts(UUID typePurposeUid) {
        return typeProductRepository.findByTypePurposeUid(typePurposeUid).stream()
                .map(p -> new SprTypeProductDTO(
                        p.getUid(), p.getTypeName(),
                        p.getTypePurpose() != null ? p.getTypePurpose().getUid() : null,
                        p.getTypePurpose() != null ? p.getTypePurpose().getTypeName() : null,
                        p.getTypePurpose() != null && p.getTypePurpose().getTypeMaterial() != null
                                ? p.getTypePurpose().getTypeMaterial().getTypeName() : null))
                .collect(Collectors.toList());
    }

    @Transactional
    public SprTypeProductDTO createTypeProduct(CreateTypeProductRequest request) {
        SprTypeProduct product = new SprTypeProduct();
        product.setUid(UUID.randomUUID());
        product.setTypeName(request.getName());
        if (request.getTypePurposeUid() != null) {
            product.setTypePurpose(typePurposeRepository.findById(request.getTypePurposeUid()).orElse(null));
        }
        typeProductRepository.save(product);
        return toTypeProductDTO(product);
    }

    @Transactional
    public SprTypeProductDTO updateTypeProduct(UUID uid, UpdateTypeProductRequest request) {
        SprTypeProduct product = typeProductRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Вид номенклатуры не найден: " + uid));
        if (request.getName() != null) product.setTypeName(request.getName());
        if (request.getTypePurposeUid() != null) {
            product.setTypePurpose(typePurposeRepository.findById(request.getTypePurposeUid()).orElse(null));
        }
        typeProductRepository.save(product);
        return toTypeProductDTO(product);
    }

    @Transactional
    public void deleteTypeProduct(UUID uid) {
        typeProductRepository.deleteById(uid);
    }

    private SprTypeProductDTO toTypeProductDTO(SprTypeProduct p) {
        return new SprTypeProductDTO(
                p.getUid(), p.getTypeName(),
                p.getTypePurpose() != null ? p.getTypePurpose().getUid() : null,
                p.getTypePurpose() != null ? p.getTypePurpose().getTypeName() : null,
                p.getTypePurpose() != null && p.getTypePurpose().getTypeMaterial() != null
                        ? p.getTypePurpose().getTypeMaterial().getTypeName() : null);
    }

    // ==================== Единицы измерения ====================

    public List<SprMeasureDTO> getMeasures() {
        return measureRepository.findAll().stream()
                .map(m -> new SprMeasureDTO(m.getUid(), m.getName(), m.getDescription()))
                .collect(Collectors.toList());
    }

    @Transactional
    public SprMeasureDTO createMeasure(CreateMeasureRequest request) {
        SprMeasure measure = new SprMeasure();
        measure.setUid(UUID.randomUUID());
        measure.setName(request.getName());
        measure.setDescription(request.getDescription());
        measureRepository.save(measure);
        return new SprMeasureDTO(measure.getUid(), measure.getName(), measure.getDescription());
    }

    @Transactional
    public SprMeasureDTO updateMeasure(UUID uid, UpdateMeasureRequest request) {
        SprMeasure measure = measureRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Единица измерения не найдена: " + uid));
        if (request.getName() != null) measure.setName(request.getName());
        if (request.getDescription() != null) measure.setDescription(request.getDescription());
        measureRepository.save(measure);
        return new SprMeasureDTO(measure.getUid(), measure.getName(), measure.getDescription());
    }

    @Transactional
    public void deleteMeasure(UUID uid) {
        measureRepository.deleteById(uid);
    }

    // ==================== Производители ====================

    public List<SprManufacturerDTO> getManufacturers() {
        return manufacturerRepository.findAll().stream()
                .map(m -> new SprManufacturerDTO(m.getUid(), m.getName(), m.getDescription()))
                .collect(Collectors.toList());
    }

    @Transactional
    public SprManufacturerDTO createManufacturer(CreateManufacturerRequest request) {
        SprManufacturer manufacturer = new SprManufacturer();
        manufacturer.setUid(UUID.randomUUID());
        manufacturer.setName(request.getName());
        manufacturer.setDescription(request.getDescription());
        manufacturerRepository.save(manufacturer);
        return new SprManufacturerDTO(manufacturer.getUid(), manufacturer.getName(), manufacturer.getDescription());
    }

    @Transactional
    public SprManufacturerDTO updateManufacturer(UUID uid, UpdateManufacturerRequest request) {
        SprManufacturer manufacturer = manufacturerRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Производитель не найден: " + uid));
        if (request.getName() != null) manufacturer.setName(request.getName());
        if (request.getDescription() != null) manufacturer.setDescription(request.getDescription());
        manufacturerRepository.save(manufacturer);
        return new SprManufacturerDTO(manufacturer.getUid(), manufacturer.getName(), manufacturer.getDescription());
    }

    @Transactional
    public void deleteManufacturer(UUID uid) {
        manufacturerRepository.deleteById(uid);
    }

    // ==================== Бренды ====================

    public List<SprBrandDTO> getBrands(UUID manufacturerUid) {
        if (manufacturerUid != null) {
            return brandRepository.findByManufacturerUid(manufacturerUid).stream()
                    .map(b -> new SprBrandDTO(
                            b.getUid(), b.getName(), b.getDescription(),
                            b.getManufacturer() != null ? b.getManufacturer().getUid() : null,
                            b.getManufacturer() != null ? b.getManufacturer().getName() : null))
                    .collect(Collectors.toList());
        }
        return brandRepository.findAll().stream()
                .map(b -> new SprBrandDTO(
                        b.getUid(), b.getName(), b.getDescription(),
                        b.getManufacturer() != null ? b.getManufacturer().getUid() : null,
                        b.getManufacturer() != null ? b.getManufacturer().getName() : null))
                .collect(Collectors.toList());
    }

    @Transactional
    public SprBrandDTO createBrand(CreateBrandRequest request) {
        SprBrand brand = new SprBrand();
        brand.setUid(UUID.randomUUID());
        brand.setName(request.getName());
        brand.setDescription(request.getDescription());
        if (request.getManufacturerUid() != null) {
            brand.setManufacturer(manufacturerRepository.findById(request.getManufacturerUid()).orElse(null));
        }
        brandRepository.save(brand);
        return new SprBrandDTO(
                brand.getUid(), brand.getName(), brand.getDescription(),
                brand.getManufacturer() != null ? brand.getManufacturer().getUid() : null,
                brand.getManufacturer() != null ? brand.getManufacturer().getName() : null);
    }

    @Transactional
    public SprBrandDTO updateBrand(UUID uid, UpdateBrandRequest request) {
        SprBrand brand = brandRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Бренд не найден: " + uid));
        if (request.getName() != null) brand.setName(request.getName());
        if (request.getDescription() != null) brand.setDescription(request.getDescription());
        if (request.getManufacturerUid() != null) {
            brand.setManufacturer(manufacturerRepository.findById(request.getManufacturerUid()).orElse(null));
        }
        brandRepository.save(brand);
        return new SprBrandDTO(
                brand.getUid(), brand.getName(), brand.getDescription(),
                brand.getManufacturer() != null ? brand.getManufacturer().getUid() : null,
                brand.getManufacturer() != null ? brand.getManufacturer().getName() : null);
    }

    @Transactional
    public void deleteBrand(UUID uid) {
        brandRepository.deleteById(uid);
    }

    // ==================== Модели ====================

    public List<SprModelOfBrandDTO> getModels(UUID brandUid) {
        if (brandUid != null) {
            return modelOfBrandRepository.findByBrandUid(brandUid).stream()
                    .map(m -> new SprModelOfBrandDTO(
                            m.getUid(), m.getName(), m.getDescription(),
                            m.getBrand() != null ? m.getBrand().getUid() : null,
                            m.getBrand() != null ? m.getBrand().getName() : null,
                            m.getBrand() != null && m.getBrand().getManufacturer() != null
                                    ? m.getBrand().getManufacturer().getName() : null))
                    .collect(Collectors.toList());
        }
        return modelOfBrandRepository.findAll().stream()
                .map(m -> new SprModelOfBrandDTO(
                        m.getUid(), m.getName(), m.getDescription(),
                        m.getBrand() != null ? m.getBrand().getUid() : null,
                        m.getBrand() != null ? m.getBrand().getName() : null,
                        m.getBrand() != null && m.getBrand().getManufacturer() != null
                                ? m.getBrand().getManufacturer().getName() : null))
                .collect(Collectors.toList());
    }

    @Transactional
    public SprModelOfBrandDTO createModel(CreateModelRequest request) {
        SprModelOfBrand model = new SprModelOfBrand();
        model.setUid(UUID.randomUUID());
        model.setName(request.getName());
        model.setDescription(request.getDescription());
        if (request.getBrandUid() != null) {
            model.setBrand(brandRepository.findById(request.getBrandUid()).orElse(null));
        }
        modelOfBrandRepository.save(model);
        return new SprModelOfBrandDTO(
                model.getUid(), model.getName(), model.getDescription(),
                model.getBrand() != null ? model.getBrand().getUid() : null,
                model.getBrand() != null ? model.getBrand().getName() : null,
                model.getBrand() != null && model.getBrand().getManufacturer() != null
                        ? model.getBrand().getManufacturer().getName() : null);
    }

    @Transactional
    public SprModelOfBrandDTO updateModel(UUID uid, UpdateModelRequest request) {
        SprModelOfBrand model = modelOfBrandRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Модель не найдена: " + uid));
        if (request.getName() != null) model.setName(request.getName());
        if (request.getDescription() != null) model.setDescription(request.getDescription());
        if (request.getBrandUid() != null) {
            model.setBrand(brandRepository.findById(request.getBrandUid()).orElse(null));
        }
        modelOfBrandRepository.save(model);
        return new SprModelOfBrandDTO(
                model.getUid(), model.getName(), model.getDescription(),
                model.getBrand() != null ? model.getBrand().getUid() : null,
                model.getBrand() != null ? model.getBrand().getName() : null,
                model.getBrand() != null && model.getBrand().getManufacturer() != null
                        ? model.getBrand().getManufacturer().getName() : null);
    }

    @Transactional
    public void deleteModel(UUID uid) {
        modelOfBrandRepository.deleteById(uid);
    }

    // ==================== Страны ====================

    public List<SprCountryDTO> getCountries() {
        return countryRepository.findAll().stream()
                .map(c -> new SprCountryDTO(c.getUid(), c.getName()))
                .collect(Collectors.toList());
    }

    @Transactional
    public SprCountryDTO createCountry(CreateCountryRequest request) {
        SprCountry country = new SprCountry();
        country.setUid(UUID.randomUUID());
        country.setName(request.getName());
        countryRepository.save(country);
        return new SprCountryDTO(country.getUid(), country.getName());
    }

    @Transactional
    public SprCountryDTO updateCountry(UUID uid, UpdateCountryRequest request) {
        SprCountry country = countryRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Страна не найдена: " + uid));
        if (request.getName() != null) country.setName(request.getName());
        countryRepository.save(country);
        return new SprCountryDTO(country.getUid(), country.getName());
    }

    @Transactional
    public void deleteCountry(UUID uid) {
        countryRepository.deleteById(uid);
    }

    // ==================== ИЗОБРАЖЕНИЯ ====================

    public List<MaterialMediaDTO> getImages(UUID materialUid) {
        return imageRepository.findByMaterialUidOrderBySortOrderAsc(materialUid).stream()
                .map(img -> new MaterialMediaDTO(
                        img.getUid(),
                        img.getMaterial().getUid(),
                        img.getFilePath(),
                        img.getOriginalName(),
                        getFileUrl(materialUid, img.getFilePath()),
                        img.getSortOrder()))
                .collect(Collectors.toList());
    }

    @Transactional
    public MaterialMediaDTO uploadImage(UUID materialUid, MultipartFile file) throws IOException {
        String fileName = saveFile(materialUid, file);

        SprMaterial material = materialRepository.findById(materialUid)
                .orElseThrow(() -> new RuntimeException("Материал не найден: " + materialUid));

        SprMaterialImage image = new SprMaterialImage();
        image.setUid(UUID.randomUUID());
        image.setMaterial(material);
        image.setFilePath(fileName);
        image.setOriginalName(file.getOriginalFilename());
        image.setSortOrder(0);
        image.setCreatedAt(LocalDateTime.now());
        imageRepository.save(image);

        return new MaterialMediaDTO(
                image.getUid(), materialUid, fileName,
                file.getOriginalFilename(),
                getFileUrl(materialUid, fileName), 0);
    }

    @Transactional
    public void deleteImage(UUID uid) {
        SprMaterialImage image = imageRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Изображение не найдено: " + uid));
        deleteFile(image.getMaterial().getUid(), image.getFilePath());
        imageRepository.delete(image);
    }

    // ==================== ЧЕРТЕЖИ ====================

    public List<MaterialMediaDTO> getBlueprints(UUID materialUid) {
        return blueprintRepository.findByMaterialUid(materialUid).stream()
                .map(bp -> new MaterialMediaDTO(
                        bp.getUid(),
                        bp.getMaterial().getUid(),
                        bp.getFilePath(),
                        bp.getOriginalName(),
                        getFileUrl(materialUid, bp.getFilePath()),
                        null))
                .collect(Collectors.toList());
    }

    @Transactional
    public MaterialMediaDTO uploadBlueprint(UUID materialUid, MultipartFile file) throws IOException {
        String fileName = saveFile(materialUid, file);

        SprMaterial material = materialRepository.findById(materialUid)
                .orElseThrow(() -> new RuntimeException("Материал не найден: " + materialUid));

        SprMaterialBlueprint blueprint = new SprMaterialBlueprint();
        blueprint.setUid(UUID.randomUUID());
        blueprint.setMaterial(material);
        blueprint.setFilePath(fileName);
        blueprint.setOriginalName(file.getOriginalFilename());
        blueprint.setCreatedAt(LocalDateTime.now());
        blueprintRepository.save(blueprint);

        return new MaterialMediaDTO(
                blueprint.getUid(), materialUid, fileName,
                file.getOriginalFilename(),
                getFileUrl(materialUid, fileName), null);
    }

    @Transactional
    public void deleteBlueprint(UUID uid) {
        SprMaterialBlueprint blueprint = blueprintRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Чертёж не найден: " + uid));
        deleteFile(blueprint.getMaterial().getUid(), blueprint.getFilePath());
        blueprintRepository.delete(blueprint);
    }

    // ==================== КОДЫ (QR, BARCODE, SKU) ====================

    public List<MaterialCodeDTO> getCodes(UUID materialUid) {
        return codeRepository.findByMaterialUidOrderByCreatedAtDesc(materialUid).stream()
                .map(c -> new MaterialCodeDTO(
                        c.getUid(),
                        c.getMaterial().getUid(),
                        c.getFilePath(),
                        c.getOriginalName(),
                        c.getCodeType(),
                        c.getCodeValue(),
                        c.getCodeKind(),
                        c.getFilePath() != null ? getFileUrl(materialUid, c.getFilePath()) : null,
                        c.getCreatedAt()))
                .collect(Collectors.toList());
    }

    public List<MaterialCodeDTO> getCodesByKind(UUID materialUid, String codeKind) {
        return codeRepository.findByMaterialUidAndCodeKindOrderByCreatedAtDesc(materialUid, codeKind).stream()
                .map(c -> new MaterialCodeDTO(
                        c.getUid(),
                        c.getMaterial().getUid(),
                        c.getFilePath(),
                        c.getOriginalName(),
                        c.getCodeType(),
                        c.getCodeValue(),
                        c.getCodeKind(),
                        c.getFilePath() != null ? getFileUrl(materialUid, c.getFilePath()) : null,
                        c.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Transactional
    public MaterialCodeDTO uploadCode(UUID materialUid, String codeType, String codeValue, String codeKind, MultipartFile file) throws IOException {
        SprMaterial material = materialRepository.findById(materialUid)
                .orElseThrow(() -> new RuntimeException("Материал не найден: " + materialUid));

        SprMaterialCode code = new SprMaterialCode();
        code.setUid(UUID.randomUUID());
        code.setMaterial(material);
        code.setCodeType(codeType != null ? codeType : "QR_CODE");
        code.setCodeValue(codeValue);
        code.setCodeKind(codeKind != null ? codeKind : "QR");

        if (file != null && !file.isEmpty()) {
            String fileName = saveFile(materialUid, file);
            code.setFilePath(fileName);
            code.setOriginalName(file.getOriginalFilename());
        }

        code.setCreatedAt(LocalDateTime.now());
        codeRepository.save(code);

        return new MaterialCodeDTO(
                code.getUid(),
                materialUid,
                code.getFilePath(),
                code.getOriginalName(),
                code.getCodeType(),
                code.getCodeValue(),
                code.getCodeKind(),
                code.getFilePath() != null ? getFileUrl(materialUid, code.getFilePath()) : null,
                code.getCreatedAt());
    }

    @Transactional
    public void deleteCode(UUID codeUid) {
        SprMaterialCode code = codeRepository.findById(codeUid).orElse(null);
        if (code != null && code.getFilePath() != null) {
            deleteFile(code.getMaterial().getUid(), code.getFilePath());
        }
        codeRepository.deleteById(codeUid);
    }

    // ==================== ДОКУМЕНТЫ ====================

    public List<MaterialDocumentDTO> getDocuments(UUID materialUid) {
        return documentRepository.findByMaterialUidOrderByCreatedAtDesc(materialUid).stream()
                .map(doc -> new MaterialDocumentDTO(
                        doc.getUid(),
                        doc.getMaterial().getUid(),
                        doc.getDocumentName(),
                        doc.getFilePath(),
                        doc.getOriginalName(),
                        getFileUrl(materialUid, doc.getFilePath()),
                        doc.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Transactional
    public MaterialDocumentDTO uploadDocument(UUID materialUid, String documentName, MultipartFile file) throws IOException {
        String fileName = saveFile(materialUid, file);

        SprMaterial material = materialRepository.findById(materialUid)
                .orElseThrow(() -> new RuntimeException("Материал не найден: " + materialUid));

        SprMaterialDocument document = new SprMaterialDocument();
        document.setUid(UUID.randomUUID());
        document.setMaterial(material);
        document.setDocumentName(documentName);
        document.setFilePath(fileName);
        document.setOriginalName(file.getOriginalFilename());
        document.setCreatedAt(LocalDateTime.now());
        documentRepository.save(document);

        return new MaterialDocumentDTO(
                document.getUid(),
                materialUid,
                document.getDocumentName(),
                document.getFilePath(),
                document.getOriginalName(),
                getFileUrl(materialUid, fileName),
                document.getCreatedAt());
    }

    @Transactional
    public void deleteDocument(UUID uid) {
        SprMaterialDocument document = documentRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Документ не найден: " + uid));
        deleteFile(document.getMaterial().getUid(), document.getFilePath());
        documentRepository.delete(document);
    }

    // ==================== СПРАВОЧНИК ПОСТАВЩИКОВ ====================

    public List<SprSupplierDTO> getSuppliers() {
        return sprSupplierRepository.findAll().stream()
                .map(s -> new SprSupplierDTO(s.getUid(), s.getName()))
                .collect(Collectors.toList());
    }

    @Transactional
    public SprSupplierDTO createSupplier(CreateSupplierRequest request) {
        SprSupplier supplier = new SprSupplier();
        supplier.setUid(UUID.randomUUID());
        supplier.setName(request.getName());
        sprSupplierRepository.save(supplier);
        return new SprSupplierDTO(supplier.getUid(), supplier.getName());
    }

    @Transactional
    public SprSupplierDTO updateSupplier(UUID uid, UpdateSupplierRequest request) {
        SprSupplier supplier = sprSupplierRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Поставщик не найден: " + uid));
        if (request.getName() != null) supplier.setName(request.getName());
        sprSupplierRepository.save(supplier);
        return new SprSupplierDTO(supplier.getUid(), supplier.getName());
    }

    @Transactional
    public void deleteSupplier(UUID uid) {
        sprSupplierRepository.deleteById(uid);
    }

    // ==================== ПРИВЯЗКА ПОСТАВЩИКОВ К МАТЕРИАЛУ ====================

    public List<MaterialSupplyDTO> getMaterialSupplies(UUID materialUid) {
        return regSuppliersRepository.findByMaterialUid(materialUid).stream()
                .map(r -> new MaterialSupplyDTO(
                        r.getUid(),
                        r.getMaterial() != null ? r.getMaterial().getUid() : materialUid,
                        r.getSupplier() != null ? r.getSupplier().getUid() : null,
                        r.getSupplier() != null ? r.getSupplier().getName() : null,
                        r.getSupplyDate(),
                        r.getDocumentName(),
                        r.getFilePath(),
                        r.getOriginalName(),
                        r.getFilePath() != null ? getFileUrl(materialUid, r.getFilePath()) : null))
                .collect(Collectors.toList());
    }

    @Transactional
    public MaterialSupplyDTO addMaterialSupply(UUID materialUid, CreateSupplyRequest request, MultipartFile file) throws IOException {
        SprMaterial material = materialRepository.findById(materialUid)
                .orElseThrow(() -> new RuntimeException("Материал не найден: " + materialUid));

        SprSupplier supplier = sprSupplierRepository.findById(request.getSupplierUid())
                .orElseThrow(() -> new RuntimeException("Поставщик не найден: " + request.getSupplierUid()));

        RegSuppliers regSuppliers = new RegSuppliers();
        regSuppliers.setUid(UUID.randomUUID());
        regSuppliers.setMaterial(material);
        regSuppliers.setSupplier(supplier);
        regSuppliers.setSupplyDate(request.getSupplyDate() != null ? request.getSupplyDate() : LocalDateTime.now());
        regSuppliers.setDocumentName(request.getDocumentName());

        if (file != null && !file.isEmpty()) {
            String fileName = saveFile(materialUid, file);
            regSuppliers.setFilePath(fileName);
            regSuppliers.setOriginalName(file.getOriginalFilename());
        }

        regSuppliersRepository.save(regSuppliers);

        return new MaterialSupplyDTO(
                regSuppliers.getUid(),
                materialUid,
                supplier.getUid(),
                supplier.getName(),
                regSuppliers.getSupplyDate(),
                regSuppliers.getDocumentName(),
                regSuppliers.getFilePath(),
                regSuppliers.getOriginalName(),
                regSuppliers.getFilePath() != null ? getFileUrl(materialUid, regSuppliers.getFilePath()) : null);
    }

    @Transactional
    public void deleteMaterialSupply(UUID supplyUid) {
        RegSuppliers regSuppliers = regSuppliersRepository.findById(supplyUid).orElse(null);
        if (regSuppliers != null && regSuppliers.getFilePath() != null) {
            deleteFile(regSuppliers.getMaterial().getUid(), regSuppliers.getFilePath());
        }
        regSuppliersRepository.deleteById(supplyUid);
    }

    // ==================== АНАЛОГИ ====================

    public List<MaterialAnalogDTO> getAnalogs(UUID materialUid) {
        return regAnalogRepository.findByMaterialUid(materialUid).stream()
                .map(a -> new MaterialAnalogDTO(
                        a.getUid(),
                        a.getMaterial().getUid(),
                        a.getAnalogMaterial().getUid(),
                        a.getAnalogMaterial().getNameMaterial(),
                        a.getAnalogMaterial().getModelOfBrand() != null ? a.getAnalogMaterial().getModelOfBrand().getName() : null,
                        a.getCompatibilityPercent(),
                        a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    public CalculateCompatibilityResponse calculateCompatibility(UUID materialUid1, UUID materialUid2) {
        SprMaterial m1 = materialRepository.findById(materialUid1)
                .orElseThrow(() -> new RuntimeException("Материал не найден: " + materialUid1));
        SprMaterial m2 = materialRepository.findById(materialUid2)
                .orElseThrow(() -> new RuntimeException("Материал не найден: " + materialUid2));

        boolean groupsMatch = true;
        if (m1.getTypeMain() != null && m2.getTypeMain() != null) {
            groupsMatch = groupsMatch && m1.getTypeMain().getUid().equals(m2.getTypeMain().getUid());
        } else {
            groupsMatch = false;
        }
        if (m1.getTypePurpose() != null && m2.getTypePurpose() != null) {
            groupsMatch = groupsMatch && m1.getTypePurpose().getUid().equals(m2.getTypePurpose().getUid());
        } else {
            groupsMatch = false;
        }
        if (m1.getTypeProduct() != null && m2.getTypeProduct() != null) {
            groupsMatch = groupsMatch && m1.getTypeProduct().getUid().equals(m2.getTypeProduct().getUid());
        } else {
            groupsMatch = false;
        }

        if (!groupsMatch) {
            return new CalculateCompatibilityResponse(0, 0, 0, false);
        }

        List<RegAttributes> attrs1 = regAttributesRepository.findByMaterialUid(materialUid1);
        List<RegAttributes> attrs2 = regAttributesRepository.findByMaterialUid(materialUid2);

        List<RegAttributes> filledAttrs1 = attrs1.stream()
                .filter(a -> a.getAttributeType() != null && a.getMeaning() != null && !a.getMeaning().isEmpty())
                .collect(Collectors.toList());

        if (filledAttrs1.isEmpty()) {
            return new CalculateCompatibilityResponse(0, 0, 0, true);
        }

        int matched = 0;
        for (RegAttributes a1 : filledAttrs1) {
            for (RegAttributes a2 : attrs2) {
                if (a2.getAttributeType() != null 
                        && a1.getAttributeType().getUid().equals(a2.getAttributeType().getUid())
                        && a1.getMeaning() != null && a1.getMeaning().equals(a2.getMeaning())) {
                    matched++;
                    break;
                }
            }
        }

        int percent = (int) Math.round((double) matched / filledAttrs1.size() * 100);

        return new CalculateCompatibilityResponse(percent, filledAttrs1.size(), matched, true);
    }

    @Transactional
    public MaterialAnalogDTO addAnalog(UUID materialUid, CreateAnalogRequest request) {
        SprMaterial material = materialRepository.findById(materialUid)
                .orElseThrow(() -> new RuntimeException("Материал не найден: " + materialUid));

        SprMaterial analogMaterial = materialRepository.findById(request.getAnalogMaterialUid())
                .orElseThrow(() -> new RuntimeException("Материал-аналог не найден: " + request.getAnalogMaterialUid()));

        RegAnalog analog = new RegAnalog();
        analog.setUid(UUID.randomUUID());
        analog.setMaterial(material);
        analog.setAnalogMaterial(analogMaterial);
        analog.setCompatibilityPercent(request.getCompatibilityPercent());
        analog.setCreatedAt(LocalDateTime.now());
        regAnalogRepository.save(analog);

        return new MaterialAnalogDTO(
                analog.getUid(),
                materialUid,
                analogMaterial.getUid(),
                analogMaterial.getNameMaterial(),
                analogMaterial.getModelOfBrand() != null ? analogMaterial.getModelOfBrand().getName() : null,
                analog.getCompatibilityPercent(),
                analog.getCreatedAt());
    }

    @Transactional
    public void deleteAnalog(UUID analogUid) {
        regAnalogRepository.deleteById(analogUid);
    }

    // ==================== РЕЙТИНГ ====================

    public List<MaterialRatingDTO> getRatings(UUID materialUid) {
        return regRatingRepository.findByMaterialUidOrderByCreatedAtDesc(materialUid).stream()
                .map(r -> new MaterialRatingDTO(
                        r.getUid(),
                        r.getMaterial().getUid(),
                        r.getRating(),
                        r.getComment(),
                        r.getAuthor(),
                        r.getCreatedAt()))
                .collect(Collectors.toList());
    }

    public Double getAverageRating(UUID materialUid) {
        return regRatingRepository.getAverageRatingByMaterialUid(materialUid);
    }

    @Transactional
    public MaterialRatingDTO addRating(UUID materialUid, AddRatingRequest request) {
        SprMaterial material = materialRepository.findById(materialUid)
                .orElseThrow(() -> new RuntimeException("Материал не найден: " + materialUid));

        RegRating rating = new RegRating();
        rating.setUid(UUID.randomUUID());
        rating.setMaterial(material);
        rating.setRating(request.getRating());
        rating.setComment(request.getComment());
        rating.setAuthor(request.getAuthor());
        rating.setCreatedAt(LocalDateTime.now());
        regRatingRepository.save(rating);

        return new MaterialRatingDTO(
                rating.getUid(),
                materialUid,
                rating.getRating(),
                rating.getComment(),
                rating.getAuthor(),
                rating.getCreatedAt());
    }

    @Transactional
    public void deleteRating(UUID ratingUid) {
        regRatingRepository.deleteById(ratingUid);
    }

    // ==================== ИНТЕГРАЦИЯ ====================

    public List<MaterialIntegrationDTO> getIntegrations(UUID materialUid) {
        return regIntegrationRepository.findByMaterialUidOrderByCreatedAtDesc(materialUid).stream()
                .map(i -> new MaterialIntegrationDTO(
                        i.getUid(),
                        i.getMaterial().getUid(),
                        i.getEvent(),
                        i.getExchangeType(),
                        i.getDirection(),
                        i.getProtocol(),
                        i.getTargetSystem(),
                        i.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Transactional
    public MaterialIntegrationDTO addIntegration(UUID materialUid, CreateIntegrationRequest request) {
        SprMaterial material = materialRepository.findById(materialUid)
                .orElseThrow(() -> new RuntimeException("Материал не найден: " + materialUid));

        RegIntegration integration = new RegIntegration();
        integration.setUid(UUID.randomUUID());
        integration.setMaterial(material);
        integration.setEvent("Объект синхронизирован");
        integration.setExchangeType(request.getExchangeType());
        integration.setDirection(request.getDirection());
        integration.setProtocol(request.getProtocol());
        integration.setTargetSystem(request.getTargetSystem());
        integration.setCreatedAt(LocalDateTime.now());
        regIntegrationRepository.save(integration);

        return new MaterialIntegrationDTO(
                integration.getUid(),
                materialUid,
                integration.getEvent(),
                integration.getExchangeType(),
                integration.getDirection(),
                integration.getProtocol(),
                integration.getTargetSystem(),
                integration.getCreatedAt());
    }

    @Transactional
    public void deleteIntegration(UUID integrationUid) {
        regIntegrationRepository.deleteById(integrationUid);
    }

    // ==================== Удаление всех медиа ====================

    @Transactional
    public void deleteAllMaterialMedia(UUID materialUid) {
        imageRepository.deleteByMaterialUid(materialUid);
        blueprintRepository.deleteByMaterialUid(materialUid);
        codeRepository.deleteByMaterialUid(materialUid);
        documentRepository.deleteByMaterialUid(materialUid);
        regSuppliersRepository.deleteByMaterialUid(materialUid);
        regAnalogRepository.deleteByMaterialUid(materialUid);
        regRatingRepository.deleteByMaterialUid(materialUid);
        regIntegrationRepository.deleteByMaterialUid(materialUid);

        try {
            Path dir = Path.of(NOMENCLATURE_UPLOAD_DIR, materialUid.toString());
            if (Files.exists(dir)) {
                try (var files = Files.list(dir)) {
                    files.forEach(f -> {
                        try { Files.deleteIfExists(f); } catch (IOException ignored) {}
                    });
                }
                Files.deleteIfExists(dir);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ==================== ЦЕНЫ ====================

    public List<MaterialPriceDTO> getPrices(UUID materialUid) {
        List<RegPrice> prices = priceRepository.findByMaterialUidOrderByPriceDateDesc(materialUid);

        List<MaterialPriceDTO> result = new ArrayList<>();
        for (int i = 0; i < prices.size(); i++) {
            RegPrice current = prices.get(i);
            Double previousPrice = (i < prices.size() - 1) ? prices.get(i + 1).getPrice() : null;
            Double priceChange = previousPrice != null ? current.getPrice() - previousPrice : null;

            String supplierName = null;
            if (current.getDocEntrance() != null && current.getDocEntrance().getSupplier() != null) {
                supplierName = current.getDocEntrance().getSupplier().getName();
            }

            result.add(new MaterialPriceDTO(
                    current.getUid(),
                    current.getPrice(),
                    current.getPriceDate(),
                    supplierName,
                    previousPrice,
                    priceChange));
        }
        return result;
    }

    @Transactional
    public MaterialPriceDTO addPrice(UUID materialUid, AddPriceRequest request) {
        SprMaterial material = materialRepository.findById(materialUid)
                .orElseThrow(() -> new RuntimeException("Материал не найден: " + materialUid));

        DocEntrance doc = new DocEntrance();
        doc.setUid(UUID.randomUUID());
        doc.setPrice(request.getPrice());
        doc.setEntranceDate(request.getPriceDate() != null ? request.getPriceDate() : LocalDateTime.now());
        if (request.getSupplierUid() != null) {
            doc.setSupplier(sprSupplierRepository.findById(request.getSupplierUid()).orElse(null));
        }
        docEntranceRepository.save(doc);

        RegPrice price = new RegPrice();
        price.setUid(UUID.randomUUID());
        price.setPrice(request.getPrice());
        price.setPriceDate(request.getPriceDate() != null ? request.getPriceDate() : LocalDateTime.now());
        price.setMaterial(material);
        price.setDocEntrance(doc);
        priceRepository.save(price);

        String supplierName = doc.getSupplier() != null ? doc.getSupplier().getName() : null;

        return new MaterialPriceDTO(
                price.getUid(),
                price.getPrice(),
                price.getPriceDate(),
                supplierName,
                null,
                null);
    }

    @Transactional
    public void deletePrice(UUID priceUid) {
        RegPrice price = priceRepository.findById(priceUid).orElse(null);
        if (price != null && price.getDocEntrance() != null) {
            docEntranceRepository.delete(price.getDocEntrance());
        }
        priceRepository.deleteById(priceUid);
    }
}
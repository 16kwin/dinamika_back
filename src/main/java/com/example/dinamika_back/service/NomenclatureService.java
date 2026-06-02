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
    private final SprMaterialQrcodeRepository qrcodeRepository;
    private final RegPriceRepository priceRepository;
    private final SprSupplierRepository suppliersRepository;
    private final DocEntranceRepository docEntranceRepository;

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
        dto.setBarcode(material.getBarcode());

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

        material.setNameMaterial(request.getName());
        material.setArticle(request.getArticle());
        material.setDescription(request.getDescription());
        material.setUsage(request.getUsage());
        material.setWasteMaterial(request.getWasteMaterial());
        material.setRecycleMaterial(request.getRecycleMaterial());
        material.setBarcode(request.getBarcode());

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
        copy.setBarcode(source.getBarcode());
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

    // ==================== QR-КОДЫ ====================

    public List<MaterialMediaDTO> getQrcodes(UUID materialUid) {
        return qrcodeRepository.findByMaterialUid(materialUid).stream()
                .map(qr -> new MaterialMediaDTO(
                        qr.getUid(),
                        qr.getMaterial().getUid(),
                        qr.getFilePath(),
                        qr.getOriginalName(),
                        getFileUrl(materialUid, qr.getFilePath()),
                        null))
                .collect(Collectors.toList());
    }

    @Transactional
    public MaterialMediaDTO uploadQrcode(UUID materialUid, MultipartFile file) throws IOException {
        String fileName = saveFile(materialUid, file);

        SprMaterial material = materialRepository.findById(materialUid)
                .orElseThrow(() -> new RuntimeException("Материал не найден: " + materialUid));

        SprMaterialQrcode qrcode = new SprMaterialQrcode();
        qrcode.setUid(UUID.randomUUID());
        qrcode.setMaterial(material);
        qrcode.setFilePath(fileName);
        qrcode.setOriginalName(file.getOriginalFilename());
        qrcode.setCreatedAt(LocalDateTime.now());
        qrcodeRepository.save(qrcode);

        return new MaterialMediaDTO(
                qrcode.getUid(), materialUid, fileName,
                file.getOriginalFilename(),
                getFileUrl(materialUid, fileName), null);
    }

    @Transactional
    public void deleteQrcode(UUID uid) {
        SprMaterialQrcode qrcode = qrcodeRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("QR-код не найден: " + uid));
        deleteFile(qrcode.getMaterial().getUid(), qrcode.getFilePath());
        qrcodeRepository.delete(qrcode);
    }

    @Transactional
    public void deleteAllMaterialMedia(UUID materialUid) {
        imageRepository.deleteByMaterialUid(materialUid);
        blueprintRepository.deleteByMaterialUid(materialUid);
        qrcodeRepository.deleteByMaterialUid(materialUid);

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
            doc.setSupplier(suppliersRepository.findById(request.getSupplierUid()).orElse(null));
        }
        docEntranceRepository.save(doc);

        RegPrice price = new RegPrice();
        price.setUid(UUID.randomUUID());
        price.setPrice(request.getPrice());
        price.setPriceDate(request.getPriceDate() != null ? request.getPriceDate() : LocalDateTime.now());
        price.setMaterial(material);
        price.setDocEntrance(doc);
        priceRepository.save(price);

        return new MaterialPriceDTO(
                price.getUid(),
                price.getPrice(),
                price.getPriceDate(),
                doc.getSupplier() != null ? doc.getSupplier().getName() : null,
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
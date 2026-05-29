package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.RegGroupMaterial;
import com.example.dinamika_back.model.SprMaterial;
import com.example.dinamika_back.model.SprTypeProduct;
import com.example.dinamika_back.model.SprTypePurpose;
import com.example.dinamika_back.repository.RegGroupMaterialRepository;
import com.example.dinamika_back.repository.SprMaterialRepository;
import com.example.dinamika_back.repository.SprTypeMaterialRepository;
import com.example.dinamika_back.repository.SprTypePurposeRepository;
import com.example.dinamika_back.repository.SprTypeProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                    // Новые поля — подтягиваем названия справочников
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
                        p.getUid(),
                        p.getTypeName(),
                        p.getTypeMaterial() != null ? p.getTypeMaterial().getUid() : null,
                        p.getTypeMaterial() != null ? p.getTypeMaterial().getTypeName() : null))
                .collect(Collectors.toList());
    }

    public List<SprTypePurposeDTO> getTypePurposes(UUID typeMaterialUid) {
        return typePurposeRepository.findByTypeMaterialUid(typeMaterialUid).stream()
                .map(p -> new SprTypePurposeDTO(
                        p.getUid(),
                        p.getTypeName(),
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
                purpose.getUid(),
                purpose.getTypeName(),
                purpose.getTypeMaterial() != null ? purpose.getTypeMaterial().getUid() : null,
                purpose.getTypeMaterial() != null ? purpose.getTypeMaterial().getTypeName() : null);
    }

    @Transactional
    public SprTypePurposeDTO updateTypePurpose(UUID uid, UpdateTypePurposeRequest request) {
        SprTypePurpose purpose = typePurposeRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Группа номенклатуры не найдена: " + uid));
        if (request.getName() != null) {
            purpose.setTypeName(request.getName());
        }
        if (request.getTypeMaterialUid() != null) {
            purpose.setTypeMaterial(typeMaterialRepository.findById(request.getTypeMaterialUid()).orElse(null));
        }
        typePurposeRepository.save(purpose);
        return new SprTypePurposeDTO(
                purpose.getUid(),
                purpose.getTypeName(),
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
                        p.getUid(),
                        p.getTypeName(),
                        p.getTypePurpose() != null ? p.getTypePurpose().getUid() : null,
                        p.getTypePurpose() != null ? p.getTypePurpose().getTypeName() : null,
                        p.getTypePurpose() != null && p.getTypePurpose().getTypeMaterial() != null
                                ? p.getTypePurpose().getTypeMaterial().getTypeName() : null))
                .collect(Collectors.toList());
    }

    public List<SprTypeProductDTO> getTypeProducts(UUID typePurposeUid) {
        return typeProductRepository.findByTypePurposeUid(typePurposeUid).stream()
                .map(p -> new SprTypeProductDTO(
                        p.getUid(),
                        p.getTypeName(),
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
        if (request.getName() != null) {
            product.setTypeName(request.getName());
        }
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
                p.getUid(),
                p.getTypeName(),
                p.getTypePurpose() != null ? p.getTypePurpose().getUid() : null,
                p.getTypePurpose() != null ? p.getTypePurpose().getTypeName() : null,
                p.getTypePurpose() != null && p.getTypePurpose().getTypeMaterial() != null
                        ? p.getTypePurpose().getTypeMaterial().getTypeName() : null);
    }
}
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.CreateGroupRequest;
import com.example.dinamika_back.dto.GroupMaterialTreeDTO;
import com.example.dinamika_back.dto.MaterialItemDTO;
import com.example.dinamika_back.dto.NomenclatureCreateResponse;
import com.example.dinamika_back.dto.NomenclatureSaveRequest;
import com.example.dinamika_back.model.RegGroupMaterial;
import com.example.dinamika_back.model.SprMaterial;
import com.example.dinamika_back.repository.RegGroupMaterialRepository;
import com.example.dinamika_back.repository.SprMaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NomenclatureService {

    private final SprMaterialRepository materialRepository;
    private final RegGroupMaterialRepository groupMaterialRepository;
    private final Random random = new Random();

    /**
     * Генерирует новый уникальный код (5 знаков) и UUID для создаваемой номенклатуры.
     */
    public NomenclatureCreateResponse generateCode() {
        Integer code;
        do {
            code = 10000 + random.nextInt(90000);
        } while (materialRepository.existsByCodeMaterial(code));

        return new NomenclatureCreateResponse(UUID.randomUUID(), code);
    }

    /**
     * Сохраняет базовые поля номенклатуры (черновик).
     */
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

        materialRepository.save(material);
    }

    /**
     * Возвращает полное дерево групп с вложенными группами и материалами.
     */
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
                    return item;
                })
                .collect(Collectors.toList());
        dto.setMaterials(materialItems);

        return dto;
    }

    /**
     * Создаёт новую группу материалов.
     */
    @Transactional
    public GroupMaterialTreeDTO createGroup(CreateGroupRequest request) {
        RegGroupMaterial group = new RegGroupMaterial();
        group.setUid(UUID.randomUUID());
        group.setGroupName(request.getName());
        group.setParentGroup(request.getParentUid());

        groupMaterialRepository.save(group);

        GroupMaterialTreeDTO dto = new GroupMaterialTreeDTO();
        dto.setUid(group.getUid());
        dto.setName(group.getGroupName());
        dto.setChildren(new ArrayList<>());
        dto.setMaterials(new ArrayList<>());
        return dto;
    }
}
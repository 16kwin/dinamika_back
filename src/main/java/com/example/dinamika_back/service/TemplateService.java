// TemplateService.java — ПОЛНЫЙ ФАЙЛ (старая иерархия категорий + новая модель ячеек)
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.*;
import com.example.dinamika_back.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final DocPatternRepository docPatternRepository;
    private final TemplateCategoryRepository categoryRepository;
    private final StationRepository stationRepository;
    private final StationConfigurationRepository configurationRepository;
    private final RegCellsRepository regCellsRepository;
    private final SprMaterialRepository materialRepository;
    private final SprCellAssignmentRepository cellAssignmentRepository;
    private final TemplateColumnSettingsService columnSettingsService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> ALL_COLUMNS_ORDER = List.of(
            "name", "number", "configurationName", "modelName",
            "stationNames", "active", "createdAt"
    );
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of(
            "name", "number", "configurationName", "modelName",
            "stationNames", "active", "createdAt"
    ));

    // ==================== ДЕРЕВО С НАСТРОЙКАМИ ====================

    public TemplatesTreeResponse getTreeWithSettings(Integer userId) {
        String columnsJson = columnSettingsService.getColumnsJson(userId);
        Set<String> visibleColumns = new LinkedHashSet<>(ALL_COLUMNS_ORDER);
        Map<String, Double> columnWidths = new HashMap<>();
        Set<String> requiredColumns = new LinkedHashSet<>(REQUIRED_COLUMNS);

        if (columnsJson != null && !columnsJson.isEmpty()) {
            parseColumnSettings(columnsJson, visibleColumns, columnWidths, requiredColumns);
        }

        List<String> orderedColumns = ALL_COLUMNS_ORDER.stream()
                .filter(visibleColumns::contains)
                .collect(Collectors.toList());

        List<TemplateCategoryDto> tree = buildCategoryTree();

        return TemplatesTreeResponse.builder()
                .tree(tree)
                .columns(orderedColumns)
                .columnWidths(columnWidths)
                .requiredColumns(new ArrayList<>(requiredColumns))
                .columnsJson(columnsJson != null ? columnsJson : "{}")
                .filtersJson(columnSettingsService.getFiltersJson(userId))
                .sortJson(columnSettingsService.getSortJson(userId))
                .currentPathJson(columnSettingsService.getCurrentPathJson(userId))
                .build();
    }

    @SuppressWarnings("unchecked")
    private void parseColumnSettings(String json, Set<String> visibleColumns, Map<String, Double> columnWidths, Set<String> requiredColumns) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            visibleColumns.clear();

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (value instanceof Boolean) {
                    if ((Boolean) value) visibleColumns.add(key);
                } else if (value instanceof Map) {
                    Map<String, Object> settings = (Map<String, Object>) value;
                    Object visible = settings.get("visible");
                    Object width = settings.get("width");
                    Object required = settings.get("required");

                    if (visible instanceof Boolean && (Boolean) visible) visibleColumns.add(key);
                    if (width instanceof Number) columnWidths.put(key, ((Number) width).doubleValue());
                    if (required instanceof Boolean && (Boolean) required) requiredColumns.add(key);
                }
            }
        } catch (Exception e) {
            visibleColumns.clear();
            visibleColumns.addAll(ALL_COLUMNS_ORDER);
        }
    }

    // ==================== ДЕРЕВО КАТЕГОРИЙ ====================

    public List<TemplateCategoryDto> buildCategoryTree() {
        List<TemplateCategory> allCategories = categoryRepository.findAll();

        Map<Long, List<TemplateCategory>> childrenMap = new HashMap<>();
        List<TemplateCategory> roots = new ArrayList<>();

        for (TemplateCategory cat : allCategories) {
            if (cat.getParentCategory() == null) {
                roots.add(cat);
            } else {
                Long parentId = cat.getParentCategory().getId();
                childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(cat);
            }
        }

        return roots.stream()
                .sorted(Comparator.comparing(TemplateCategory::getCode, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(root -> buildCategoryNode(root, childrenMap))
                .collect(Collectors.toList());
    }

    private TemplateCategoryDto buildCategoryNode(TemplateCategory category, Map<Long, List<TemplateCategory>> childrenMap) {
        List<TemplateCategory> children = childrenMap.getOrDefault(category.getId(), Collections.emptyList());
        List<TemplateCategoryDto> childrenDtos = children.stream()
                .sorted(Comparator.comparing(TemplateCategory::getCode, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(child -> buildCategoryNode(child, childrenMap))
                .collect(Collectors.toList());

        List<TemplateDto> templates = docPatternRepository.findByCategoryIdOrderByNumberAsc(category.getId()).stream()
                .map(this::toTemplateDto)
                .collect(Collectors.toList());

        return TemplateCategoryDto.builder()
                .id(category.getId())
                .uid(category.getUid())
                .name(category.getName())
                .code(category.getCode())
                .parentCategoryId(category.getParentCategory() != null ? category.getParentCategory().getId() : null)
                .parentCategoryUid(category.getParentCategory() != null ? category.getParentCategory().getUid() : null)
                .parentCategoryName(category.getParentCategory() != null ? category.getParentCategory().getName() : null)
                .children(childrenDtos)
                .templates(templates)
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    // ==================== КАТЕГОРИИ ====================

    public List<TemplateCategoryDto> getAllCategories() {
        return buildCategoryTree();
    }

    public TemplateCategoryDto getCategoryById(Long id) {
        TemplateCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Категория не найдена: " + id));
        return toCategoryDto(category);
    }

    public TemplateCategoryDto getCategoryByUid(UUID uid) {
        TemplateCategory category = categoryRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Категория не найдена: " + uid));
        return toCategoryDto(category);
    }

    @Transactional
    public TemplateCategoryDto createCategory(CreateTemplateCategoryRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Имя категории обязательно");
        }

        Long parentId = null;
        TemplateCategory parent = null;

        if (request.getParentCategoryUid() != null) {
            parent = categoryRepository.findByUid(request.getParentCategoryUid())
                    .orElseThrow(() -> new RuntimeException("Родительская категория не найдена"));
            parentId = parent.getId();

            if (categoryRepository.existsByNameAndParentCategoryId(request.getName(), parentId)) {
                throw new RuntimeException("Категория с таким именем уже существует в этой родительской");
            }
        } else {
            if (categoryRepository.existsByNameAndParentCategoryIsNull(request.getName())) {
                throw new RuntimeException("Корневая категория с таким именем уже существует");
            }
        }

        Integer nextCode = categoryRepository.findMaxCode() + 1;

        TemplateCategory category = new TemplateCategory();
        category.setUid(UUID.randomUUID());
        category.setName(request.getName());
        category.setParentCategory(parent);
        category.setCode(nextCode);
        categoryRepository.save(category);
        return toCategoryDto(category);
    }

    @Transactional
    public TemplateCategoryDto updateCategory(Long id, CreateTemplateCategoryRequest request) {
        TemplateCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Категория не найдена: " + id));

        if (request.getName() != null && !request.getName().isBlank()) {
            category.setName(request.getName());
        }

        if (request.getParentCategoryUid() != null) {
            TemplateCategory newParent = categoryRepository.findByUid(request.getParentCategoryUid())
                    .orElseThrow(() -> new RuntimeException("Родительская категория не найдена"));

            if (newParent.getId().equals(category.getId())) {
                throw new RuntimeException("Категория не может быть родителем самой себе");
            }

            if (isDescendant(category.getId(), newParent.getId())) {
                throw new RuntimeException("Нельзя переместить категорию в своего потомка");
            }

            category.setParentCategory(newParent);
        } else {
            category.setParentCategory(null);
        }

        categoryRepository.save(category);
        return toCategoryDto(category);
    }

    private boolean isDescendant(Long ancestorId, Long candidateId) {
        Long currentId = candidateId;
        Set<Long> visited = new HashSet<>();
        while (currentId != null && !visited.contains(currentId)) {
            if (currentId.equals(ancestorId)) return true;
            visited.add(currentId);
            TemplateCategory cat = categoryRepository.findById(currentId).orElse(null);
            if (cat == null || cat.getParentCategory() == null) return false;
            currentId = cat.getParentCategory().getId();
        }
        return false;
    }

    @Transactional
    public void deleteCategory(Long id) {
        TemplateCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Категория не найдена: " + id));

        if (hasTemplatesRecursive(category)) {
            throw new RuntimeException("Нельзя удалить категорию, в которой есть шаблоны (или во вложенных)");
        }

        deleteCategoryRecursive(category);
    }

    private boolean hasTemplatesRecursive(TemplateCategory category) {
        if (!docPatternRepository.findByCategoryId(category.getId()).isEmpty()) return true;
        for (TemplateCategory child : categoryRepository.findByParentCategoryId(category.getId())) {
            if (hasTemplatesRecursive(child)) return true;
        }
        return false;
    }

    private void deleteCategoryRecursive(TemplateCategory category) {
        for (TemplateCategory child : categoryRepository.findByParentCategoryId(category.getId())) {
            deleteCategoryRecursive(child);
        }
        categoryRepository.delete(category);
    }

    @Transactional
    public TemplateCategoryDto moveCategory(UUID categoryUid, UUID newParentUid) {
        TemplateCategory category = categoryRepository.findByUid(categoryUid)
                .orElseThrow(() -> new RuntimeException("Категория не найдена: " + categoryUid));

        TemplateCategory newParent = null;
        if (newParentUid != null) {
            newParent = categoryRepository.findByUid(newParentUid)
                    .orElseThrow(() -> new RuntimeException("Родительская категория не найдена: " + newParentUid));

            if (newParent.getId().equals(category.getId())) {
                throw new RuntimeException("Категория не может быть родителем самой себе");
            }
            if (isDescendant(category.getId(), newParent.getId())) {
                throw new RuntimeException("Нельзя переместить категорию в своего потомка");
            }
        }

        category.setParentCategory(newParent);
        categoryRepository.save(category);
        return toCategoryDto(category);
    }

    @Transactional
    public TemplateDto moveTemplate(UUID templateUid, UUID newCategoryUid) {
        DocPattern template = docPatternRepository.findById(templateUid)
                .orElseThrow(() -> new RuntimeException("Шаблон не найден: " + templateUid));

        if (newCategoryUid != null) {
            TemplateCategory newCategory = categoryRepository.findByUid(newCategoryUid)
                    .orElseThrow(() -> new RuntimeException("Категория не найдена: " + newCategoryUid));
            template.setCategory(newCategory);
        } else {
            template.setCategory(null);
        }

        docPatternRepository.save(template);
        return toTemplateDto(template);
    }

    // ==================== ШАБЛОНЫ ====================

    public List<TemplateDto> getAllTemplates() {
        return docPatternRepository.findAllByOrderByNumberAsc().stream()
                .map(this::toTemplateDto)
                .collect(Collectors.toList());
    }

    public List<TemplateDto> getTemplatesByCategory(Long categoryId) {
        return docPatternRepository.findByCategoryIdOrderByNumberAsc(categoryId).stream()
                .map(this::toTemplateDto)
                .collect(Collectors.toList());
    }

    public TemplateDto getTemplateById(UUID uid) {
        DocPattern template = docPatternRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Шаблон не найден: " + uid));
        return toTemplateDto(template);
    }

    @Transactional
    public TemplateDto createTemplate(TemplateRequest request) {
        Long nextNumber = docPatternRepository.findMaxNumber() + 1;

        DocPattern template = new DocPattern();
        template.setUid(UUID.randomUUID());
        template.setNamePattern(request.getName());
        template.setNumber(nextNumber);
        template.setConfiguration(request.getConfiguration() != null ? request.getConfiguration() : "");
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());

        if (request.getConfigurationUid() != null) {
            StationConfiguration config = configurationRepository.findById(request.getConfigurationUid())
                    .orElseThrow(() -> new RuntimeException("Конфигурация не найдена: " + request.getConfigurationUid()));
            template.setStationConfiguration(config);
        }

        if (request.getCategoryId() != null) {
            TemplateCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Категория не найдена: " + request.getCategoryId()));
            template.setCategory(category);
        }

        docPatternRepository.save(template);
        return toTemplateDto(template);
    }

    @Transactional
    public TemplateDto updateTemplate(UUID uid, TemplateRequest request) {
        DocPattern template = docPatternRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Шаблон не найден: " + uid));

        if (request.getName() != null) {
            template.setNamePattern(request.getName());
        }
        if (request.getConfiguration() != null) {
            template.setConfiguration(request.getConfiguration());
        }
        if (request.getConfigurationUid() != null) {
            StationConfiguration config = configurationRepository.findById(request.getConfigurationUid())
                    .orElseThrow(() -> new RuntimeException("Конфигурация не найдена: " + request.getConfigurationUid()));
            template.setStationConfiguration(config);
        }
        if (request.getCategoryId() != null) {
            TemplateCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Категория не найдена: " + request.getCategoryId()));
            template.setCategory(category);
        }

        docPatternRepository.save(template);
        return toTemplateDto(template);
    }

    @Transactional
    public void deleteTemplate(UUID uid) {
        DocPattern template = docPatternRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Шаблон не найден: " + uid));
        docPatternRepository.delete(template);
    }

    @Transactional
    public TemplateDto copyTemplate(TemplateCopyRequest request) {
        DocPattern source = docPatternRepository.findById(request.getSourceTemplateUid())
                .orElseThrow(() -> new RuntimeException("Исходный шаблон не найден: " + request.getSourceTemplateUid()));

        Long nextNumber = docPatternRepository.findMaxNumber() + 1;
        LocalDateTime now = LocalDateTime.now();

        DocPattern copy = new DocPattern();
        copy.setUid(UUID.randomUUID());
        copy.setNamePattern(
                request.getName() != null && !request.getName().isBlank()
                        ? request.getName()
                        : source.getNamePattern() + " (копия)");
        copy.setNumber(nextNumber);
        copy.setConfiguration(source.getConfiguration());
        copy.setStationConfiguration(source.getStationConfiguration());
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);

        Long targetCategoryId = request.getTargetCategoryId() != null
                ? request.getTargetCategoryId()
                : (source.getCategory() != null ? source.getCategory().getId() : null);

        if (targetCategoryId != null) {
            TemplateCategory category = categoryRepository.findById(targetCategoryId)
                    .orElseThrow(() -> new RuntimeException("Категория не найдена: " + targetCategoryId));
            copy.setCategory(category);
        }

        copy.setTotalCells(source.getTotalCells());
        copy.setFilledCells(0);
        copy.setFreeCells(source.getTotalCells());

        docPatternRepository.save(copy);

        // === Копирование ячеек источника (новая модель) ===
        List<RegCells> sourceCells = regCellsRepository.findByDocPatternUid(source.getUid());
        for (RegCells src : sourceCells) {
            RegCells newCell = new RegCells();
            newCell.setUid(UUID.randomUUID());
            newCell.setDocPattern(copy);
            newCell.setNumberCell(src.getNumberCell());
            newCell.setColumnNumber(src.getColumnNumber());
            newCell.setDrumNumber(src.getDrumNumber());
            newCell.setCellAssignment(src.getCellAssignment());
            newCell.setMaterial(src.getMaterial());
            newCell.setQuantity(src.getQuantity());
            newCell.setReturnToThisCell(src.getReturnToThisCell() != null ? src.getReturnToThisCell() : false);
            newCell.setIsIndividual(src.getIsIndividual() != null ? src.getIsIndividual() : false);
            regCellsRepository.save(newCell);
        }

        recalcTemplateStats(copy);
        return toTemplateDto(copy);
    }

    // ==================== БАТЧ ЯЧЕЕК ====================

    @Transactional
    public void saveBatchCells(UUID templateUid, SaveBatchCellsRequest request) {
        DocPattern template = docPatternRepository.findById(templateUid)
                .orElseThrow(() -> new RuntimeException("Шаблон не найден: " + templateUid));

        regCellsRepository.deleteByDocPatternUid(templateUid);

        if (request.getCells() != null) {
            for (SaveBatchCellsRequest.BatchCellItem item : request.getCells()) {
                RegCells cell = new RegCells();
                cell.setUid(UUID.randomUUID());
                cell.setDocPattern(template);
                cell.setNumberCell(item.getNumberCell());
                cell.setColumnNumber(item.getColumnNumber());
                cell.setDrumNumber(item.getDrumNumber());
                cell.setQuantity(item.getQuantity());
                cell.setReturnToThisCell(item.getReturnToThisCell() != null ? item.getReturnToThisCell() : false);
                cell.setIsIndividual(item.getIsIndividual() != null ? item.getIsIndividual() : false);

                if (item.getCellAssignmentUid() != null) {
                    SprCellAssignment assignment = cellAssignmentRepository.findById(item.getCellAssignmentUid())
                            .orElseThrow(() -> new RuntimeException("Назначение не найдено: " + item.getCellAssignmentUid()));
                    cell.setCellAssignment(assignment);
                }
                if (item.getMaterialUid() != null) {
                    SprMaterial material = materialRepository.findById(item.getMaterialUid())
                            .orElseThrow(() -> new RuntimeException("Материал не найден: " + item.getMaterialUid()));
                    cell.setMaterial(material);
                }

                regCellsRepository.save(cell);
            }
        }

        recalcTemplateStats(template);
    }

    // ==================== НАЗНАЧЕНИЯ ЯЧЕЕК (справочник) ====================

    public List<CellAssignmentDto> getAllCellAssignments() {
        return cellAssignmentRepository.findAll().stream()
                .map(this::toCellAssignmentDto)
                .collect(Collectors.toList());
    }

    // ==================== СТАНЦИИ ШАБЛОНА ====================

    public List<String> getTemplateStations(UUID templateUid) {
        List<Station> stations = stationRepository.findByActiveTemplateUid(templateUid);
        return stations.stream()
                .map(Station::getName)
                .collect(Collectors.toList());
    }

    // ==================== ЯЧЕЙКИ ШАБЛОНА ====================

    public List<CellDto> getTemplateCells(UUID templateUid) {
        return regCellsRepository.findByDocPatternUid(templateUid).stream()
                .map(this::toCellDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public CellDto createCell(CreateCellRequest request) {
        DocPattern template = docPatternRepository.findById(request.getDocPatternUid())
                .orElseThrow(() -> new RuntimeException("Шаблон не найден: " + request.getDocPatternUid()));

        RegCells cell = new RegCells();
        cell.setUid(UUID.randomUUID());
        cell.setDocPattern(template);
        cell.setQuantity(request.getQuantity());
        cell.setNumberCell(request.getNumberCell());
        cell.setColumnNumber(request.getColumnNumber());
        cell.setDrumNumber(request.getDrumNumber());

        if (request.getCellAssignmentUid() != null) {
            SprCellAssignment assignment = cellAssignmentRepository.findById(request.getCellAssignmentUid())
                    .orElseThrow(() -> new RuntimeException("Назначение не найдено: " + request.getCellAssignmentUid()));
            cell.setCellAssignment(assignment);
        }
        if (request.getMaterialUid() != null) {
            SprMaterial material = materialRepository.findById(request.getMaterialUid())
                    .orElseThrow(() -> new RuntimeException("Материал не найден: " + request.getMaterialUid()));
            cell.setMaterial(material);
        }

        if (request.getReturnToThisCell() != null) {
            cell.setReturnToThisCell(request.getReturnToThisCell());
        }
        if (request.getIsIndividual() != null) {
            cell.setIsIndividual(request.getIsIndividual());
        }

        regCellsRepository.save(cell);
        recalcTemplateStats(template);

        return toCellDto(cell);
    }

    @Transactional
    public CellDto updateCell(UUID cellUid, CellRequest request) {
        RegCells cell = regCellsRepository.findById(cellUid)
                .orElseThrow(() -> new RuntimeException("Ячейка не найдена: " + cellUid));

        if (request.getCellAssignmentUid() != null) {
            SprCellAssignment assignment = cellAssignmentRepository.findById(request.getCellAssignmentUid())
                    .orElseThrow(() -> new RuntimeException("Назначение не найдено: " + request.getCellAssignmentUid()));
            cell.setCellAssignment(assignment);
        } else {
            cell.setCellAssignment(null);
        }

        if (request.getMaterialUid() != null) {
            SprMaterial material = materialRepository.findById(request.getMaterialUid())
                    .orElseThrow(() -> new RuntimeException("Материал не найден: " + request.getMaterialUid()));
            cell.setMaterial(material);
        } else {
            cell.setMaterial(null);
        }

        cell.setQuantity(request.getQuantity());

        if (request.getReturnToThisCell() != null) {
            cell.setReturnToThisCell(request.getReturnToThisCell());
        }
        if (request.getIsIndividual() != null) {
            cell.setIsIndividual(request.getIsIndividual());
        }

        regCellsRepository.save(cell);
        recalcTemplateStats(cell.getDocPattern());

        return toCellDto(cell);
    }

    @Transactional
    public void clearCell(UUID cellUid) {
        RegCells cell = regCellsRepository.findById(cellUid)
                .orElseThrow(() -> new RuntimeException("Ячейка не найдена: " + cellUid));

        DocPattern template = cell.getDocPattern();
        cell.clear();
        regCellsRepository.save(cell);
        recalcTemplateStats(template);
    }

    @Transactional
    public void clearBatchCells(ClearBatchRequest request) {
        List<RegCells> cells = regCellsRepository.findAllById(request.getCellUids());
        DocPattern template = null;

        for (RegCells cell : cells) {
            if (template == null) {
                template = cell.getDocPattern();
            }
            cell.clear();
        }

        regCellsRepository.saveAll(cells);

        if (template != null) {
            recalcTemplateStats(template);
        }
    }

    private void recalcTemplateStats(DocPattern template) {
        List<RegCells> cells = regCellsRepository.findByDocPatternUid(template.getUid());
        long filled = cells.stream().filter(c -> c.getMaterial() != null).count();
        template.setFilledCells((int) filled);
        template.setFreeCells(template.getTotalCells() - (int) filled);
        docPatternRepository.save(template);
    }

    // ==================== МАППИНГ ====================

    private TemplateCategoryDto toCategoryDto(TemplateCategory category) {
        return TemplateCategoryDto.builder()
                .id(category.getId())
                .uid(category.getUid())
                .name(category.getName())
                .code(category.getCode())
                .parentCategoryId(category.getParentCategory() != null ? category.getParentCategory().getId() : null)
                .parentCategoryUid(category.getParentCategory() != null ? category.getParentCategory().getUid() : null)
                .parentCategoryName(category.getParentCategory() != null ? category.getParentCategory().getName() : null)
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    private TemplateDto toTemplateDto(DocPattern template) {
        List<Station> stations = stationRepository.findByActiveTemplateUid(template.getUid());
        List<String> stationNames = stations.stream()
                .map(Station::getName)
                .collect(Collectors.toList());

        StationConfiguration config = template.getStationConfiguration();
        String configName = null;
        UUID configUid = null;
        String modelName = null;

        if (config != null) {
            configUid = config.getUid();
            configName = config.getName();
            if (config.getModel() != null) {
                modelName = config.getModel().getName();
            }
        }

        return TemplateDto.builder()
                .uid(template.getUid())
                .name(template.getNamePattern())
                .number(template.getNumber())
                .categoryId(template.getCategory() != null ? template.getCategory().getId() : null)
                .categoryName(template.getCategory() != null ? template.getCategory().getName() : null)
                .configuration(template.getConfiguration())
                .configurationUid(configUid)
                .configurationName(configName)
                .modelName(modelName)
                .totalCells(template.getTotalCells())
                .filledCells(template.getFilledCells())
                .freeCells(template.getFreeCells())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .active(!stationNames.isEmpty())
                .stationNames(stationNames)
                .build();
    }

    private CellDto toCellDto(RegCells cell) {
        SprCellAssignment assignment = cell.getCellAssignment();
        UUID assignmentUid = null;
        String assignmentName = null;
        UUID assignmentTypeUid = null;
        String assignmentTypeName = null;

        if (assignment != null) {
            assignmentUid = assignment.getUid();
            assignmentName = assignment.getName();
            if (assignment.getType() != null) {
                assignmentTypeUid = assignment.getType().getUid();
                assignmentTypeName = assignment.getType().getTypeName();
            }
        }

        return CellDto.builder()
                .uid(cell.getUid())
                .numberCell(cell.getNumberCell())
                .columnNumber(cell.getColumnNumber())
                .drumNumber(cell.getDrumNumber())
                .cellAssignmentUid(assignmentUid)
                .cellAssignmentName(assignmentName)
                .cellAssignmentTypeUid(assignmentTypeUid)
                .cellAssignmentTypeName(assignmentTypeName)
                .materialUid(cell.getMaterial() != null ? cell.getMaterial().getUid() : null)
                .materialName(cell.getMaterial() != null ? cell.getMaterial().getNameMaterial() : null)
                .materialArticle(cell.getMaterial() != null ? cell.getMaterial().getArticle() : null)
                .quantity(cell.getQuantity())
                .returnToThisCell(cell.getReturnToThisCell())
                .isIndividual(cell.getIsIndividual())
                .build();
    }

    private CellAssignmentDto toCellAssignmentDto(SprCellAssignment assignment) {
        return CellAssignmentDto.builder()
                .uid(assignment.getUid())
                .name(assignment.getName())
                .typeUid(assignment.getType() != null ? assignment.getType().getUid() : null)
                .typeName(assignment.getType() != null ? assignment.getType().getTypeName() : null)
                .build();
    }
}
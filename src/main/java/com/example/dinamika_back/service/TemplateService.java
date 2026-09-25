// TemplateService.java — ПОЛНЫЙ ФАЙЛ (с историей изменений шаблона)
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

    // === История ===
    private final TemplateEventLogRepository templateEventLogRepository;
    private final UserService userService;

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

        String oldCategoryName = template.getCategory() != null ? template.getCategory().getName() : null;

        if (newCategoryUid != null) {
            TemplateCategory newCategory = categoryRepository.findByUid(newCategoryUid)
                    .orElseThrow(() -> new RuntimeException("Категория не найдена: " + newCategoryUid));
            template.setCategory(newCategory);
        } else {
            template.setCategory(null);
        }

        docPatternRepository.save(template);

        String newCategoryName = template.getCategory() != null ? template.getCategory().getName() : null;

        logTemplateEvent(
                template.getUid(),
                null,
                "MOVE",
                "'" + template.getNamePattern() + "': Значение поля 'Категория' изменено с '" +
                        (oldCategoryName != null ? oldCategoryName : "null") + "' на '" +
                        (newCategoryName != null ? newCategoryName : "null") + "'",
                "Категория",
                oldCategoryName,
                newCategoryName,
                "Через карточку"
        );

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

        logTemplateEvent(
                template.getUid(),
                null,
                "CREATE",
                "Создание шаблона: '" + template.getNamePattern() + "'",
                null,
                null,
                null,
                "Через карточку"
        );

        return toTemplateDto(template);
    }

    @Transactional
    public TemplateDto updateTemplate(UUID uid, TemplateRequest request) {
        DocPattern template = docPatternRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Шаблон не найден: " + uid));

        if (request.getName() != null) {
            String oldName = template.getNamePattern();
            if (!Objects.equals(oldName, request.getName())) {
                logTemplateEvent(template.getUid(), null, "UPDATE",
                        "'" + oldName + "': Значение поля 'Наименование' изменено с '" + oldName + "' на '" + request.getName() + "'",
                        "Наименование", oldName, request.getName(), "Через карточку");
                template.setNamePattern(request.getName());
            }
        }
        if (request.getConfiguration() != null) {
            String oldConfig = template.getConfiguration();
            if (!Objects.equals(oldConfig, request.getConfiguration())) {
                logTemplateEvent(template.getUid(), null, "UPDATE",
                        "'" + template.getNamePattern() + "': Значение поля 'Конфигурация' изменено с '" +
                                (oldConfig != null ? oldConfig : "null") + "' на '" + request.getConfiguration() + "'",
                        "Конфигурация", oldConfig, request.getConfiguration(), "Через карточку");
                template.setConfiguration(request.getConfiguration());
            }
        }
        if (request.getConfigurationUid() != null) {
            StationConfiguration config = configurationRepository.findById(request.getConfigurationUid())
                    .orElseThrow(() -> new RuntimeException("Конфигурация не найдена: " + request.getConfigurationUid()));
            UUID oldConfigUid = template.getStationConfiguration() != null ? template.getStationConfiguration().getUid() : null;
            String oldConfigName = template.getStationConfiguration() != null ? template.getStationConfiguration().getName() : null;
            if (!Objects.equals(oldConfigUid, config.getUid())) {
                logTemplateEvent(template.getUid(), null, "UPDATE",
                        "'" + template.getNamePattern() + "': Значение поля 'Конфигурация станции' изменено с '" +
                                (oldConfigName != null ? oldConfigName : "null") + "' на '" + config.getName() + "'",
                        "Конфигурация станции", oldConfigName, config.getName(), "Через карточку");
                template.setStationConfiguration(config);
            }
        }
        if (request.getCategoryId() != null) {
            TemplateCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Категория не найдена: " + request.getCategoryId()));
            Long oldCatId = template.getCategory() != null ? template.getCategory().getId() : null;
            String oldCatName = template.getCategory() != null ? template.getCategory().getName() : null;
            if (!Objects.equals(oldCatId, category.getId())) {
                logTemplateEvent(template.getUid(), null, "UPDATE",
                        "'" + template.getNamePattern() + "': Значение поля 'Категория' изменено с '" +
                                (oldCatName != null ? oldCatName : "null") + "' на '" + category.getName() + "'",
                        "Категория", oldCatName, category.getName(), "Через карточку");
                template.setCategory(category);
            }
        }

        template.setUpdatedAt(LocalDateTime.now());
        docPatternRepository.save(template);
        return toTemplateDto(template);
    }

    @Transactional
    public void deleteTemplate(UUID uid) {
        DocPattern template = docPatternRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Шаблон не найден: " + uid));

        String name = template.getNamePattern();

        // Логируем ДО удаления — но таблица template_event_log имеет ON DELETE CASCADE,
        // поэтому запись удалится вместе с шаблоном. Чтобы история сохранилась при
        // удалении, можно либо не удалять шаблон физически (soft delete), либо
        // хранить историю вне шаблона.
        //
        // Текущая миграция: template_uid REFERENCES doc_pattern(uid) ON DELETE CASCADE —
        // значит при удалении шаблона история исчезнет.
        //
        // Если это нежелательно — надо в миграции поменять на ON DELETE SET NULL
        // и тогда строка истории останется с template_uid = NULL.
        // Сейчас пишем событие и полагаемся на то, что если политика CASCADE —
        // запись удалится вместе с шаблоном (что логично — история мёртвого шаблона не нужна).
        logTemplateEvent(
                template.getUid(),
                null,
                "DELETE",
                "Удаление шаблона: '" + name + "'",
                null,
                name,
                null,
                "Через карточку"
        );

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

        // Копирование ячеек
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

        logTemplateEvent(
                copy.getUid(),
                null,
                "COPY",
                "Создание копии шаблона: '" + source.getNamePattern() + "' → '" + copy.getNamePattern() +
                        "' (скопировано ячеек: " + sourceCells.size() + ")",
                null,
                source.getNamePattern(),
                copy.getNamePattern(),
                "Копирование шаблона"
        );

        return toTemplateDto(copy);
    }

    // ==================== БАТЧ ЯЧЕЕК ====================

    @Transactional
    public void saveBatchCells(UUID templateUid, SaveBatchCellsRequest request) {
        DocPattern template = docPatternRepository.findById(templateUid)
                .orElseThrow(() -> new RuntimeException("Шаблон не найден: " + templateUid));

        // 1. Снимок старого состояния
        List<RegCells> oldCells = regCellsRepository.findByDocPatternUid(templateUid);
        Map<String, RegCells> oldMap = new HashMap<>();
        for (RegCells c : oldCells) {
            oldMap.put(cellKey(c.getDrumNumber(), c.getColumnNumber(), c.getNumberCell()), c);
        }

        // 2. Индексируем новый набор
        List<SaveBatchCellsRequest.BatchCellItem> newItems = request.getCells() != null
                ? request.getCells() : Collections.emptyList();
        Set<String> newKeys = new HashSet<>();

        // 3. Собираем diff — до фактического удаления/вставки
        List<String> diffs = new ArrayList<>();
        int createdCount = 0;
        int updatedCount = 0;
        int deletedCount = 0;

        Map<String, SaveBatchCellsRequest.BatchCellItem> newMap = new HashMap<>();
        for (SaveBatchCellsRequest.BatchCellItem item : newItems) {
            String key = cellKey(item.getDrumNumber(), item.getColumnNumber(), item.getNumberCell());
            newMap.put(key, item);
            newKeys.add(key);
        }

        // Изменённые или созданные
        for (Map.Entry<String, SaveBatchCellsRequest.BatchCellItem> e : newMap.entrySet()) {
            String key = e.getKey();
            SaveBatchCellsRequest.BatchCellItem ni = e.getValue();
            RegCells old = oldMap.get(key);

            String cellLabel = formatCellLabel(ni.getDrumNumber(), ni.getColumnNumber(), ni.getNumberCell());

            if (old == null) {
                createdCount++;
                diffs.add("Создана ячейка " + cellLabel);
                continue;
            }

            // Сравниваем поля
            String oldAssignUid = old.getCellAssignment() != null ? old.getCellAssignment().getUid().toString() : null;
            String newAssignUid = ni.getCellAssignmentUid() != null ? ni.getCellAssignmentUid().toString() : null;
            if (!Objects.equals(oldAssignUid, newAssignUid)) {
                String oldName = old.getCellAssignment() != null ? old.getCellAssignment().getName() : null;
                String newName = null;
                if (ni.getCellAssignmentUid() != null) {
                    SprCellAssignment a = cellAssignmentRepository.findById(ni.getCellAssignmentUid()).orElse(null);
                    newName = a != null ? a.getName() : null;
                }
                updatedCount++;
                diffs.add("Ячейка " + cellLabel + ": Назначение '" + (oldName != null ? oldName : "—") + "' → '" + (newName != null ? newName : "—") + "'");
            }

            String oldMatUid = old.getMaterial() != null ? old.getMaterial().getUid().toString() : null;
            String newMatUid = ni.getMaterialUid() != null ? ni.getMaterialUid().toString() : null;
            if (!Objects.equals(oldMatUid, newMatUid)) {
                String oldName = old.getMaterial() != null ? old.getMaterial().getNameMaterial() : null;
                String newName = null;
                if (ni.getMaterialUid() != null) {
                    SprMaterial m = materialRepository.findById(ni.getMaterialUid()).orElse(null);
                    newName = m != null ? m.getNameMaterial() : null;
                }
                updatedCount++;
                diffs.add("Ячейка " + cellLabel + ": Номенклатура '" + (oldName != null ? oldName : "—") + "' → '" + (newName != null ? newName : "—") + "'");
            }

            if (!Objects.equals(old.getQuantity(), ni.getQuantity())) {
                updatedCount++;
                diffs.add("Ячейка " + cellLabel + ": Количество " + (old.getQuantity() != null ? old.getQuantity() : "—") + " → " + (ni.getQuantity() != null ? ni.getQuantity() : "—"));
            }

            boolean oldReturn = old.getReturnToThisCell() != null && old.getReturnToThisCell();
            boolean newReturn = ni.getReturnToThisCell() != null && ni.getReturnToThisCell();
            if (oldReturn != newReturn) {
                updatedCount++;
                diffs.add("Ячейка " + cellLabel + ": Возврат в ячейку " + (oldReturn ? "Да" : "Нет") + " → " + (newReturn ? "Да" : "Нет"));
            }

            boolean oldInd = old.getIsIndividual() != null && old.getIsIndividual();
            boolean newInd = ni.getIsIndividual() != null && ni.getIsIndividual();
            if (oldInd != newInd) {
                updatedCount++;
                diffs.add("Ячейка " + cellLabel + ": Индивидуальная " + (oldInd ? "Да" : "Нет") + " → " + (newInd ? "Да" : "Нет"));
            }
        }

        // Удалённые
        for (String oldKey : oldMap.keySet()) {
            if (!newKeys.contains(oldKey)) {
                RegCells old = oldMap.get(oldKey);
                deletedCount++;
                String cellLabel = formatCellLabel(old.getDrumNumber(), old.getColumnNumber(), old.getNumberCell());
                diffs.add("Удалена ячейка " + cellLabel);
            }
        }

        // 4. Фактическая запись
        regCellsRepository.deleteByDocPatternUid(templateUid);

        for (SaveBatchCellsRequest.BatchCellItem item : newItems) {
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

        recalcTemplateStats(template);

        // 5. Логируем одним событием BATCH_SAVE + детализацию
        if (createdCount > 0 || updatedCount > 0 || deletedCount > 0) {
            String summary = String.format(
                    "Массовое сохранение ячеек шаблона '%s': создано %d, изменено %d, удалено %d",
                    template.getNamePattern(), createdCount, updatedCount, deletedCount
            );

            logTemplateEvent(
                    templateUid,
                    null,
                    "BATCH_SAVE",
                    summary,
                    null,
                    null,
                    null,
                    "Массовое сохранение"
            );

            // Детальные строки — по одной на изменение, но не более N (например, 200),
            // чтобы не раздувать историю. Остальное — сводка.
            int limit = Math.min(diffs.size(), 200);
            for (int i = 0; i < limit; i++) {
                logTemplateEvent(
                        templateUid,
                        null,
                        "BATCH_ITEM",
                        diffs.get(i),
                        null,
                        null,
                        null,
                        "Массовое сохранение"
                );
            }
            if (diffs.size() > limit) {
                logTemplateEvent(
                        templateUid,
                        null,
                        "BATCH_ITEM",
                        "… и ещё " + (diffs.size() - limit) + " изменений (сокращено)",
                        null,
                        null,
                        null,
                        "Массовое сохранение"
                );
            }
        }
    }

    private String cellKey(Integer drum, Integer column, Integer number) {
        return (drum != null ? drum : 0) + "-" + (column != null ? column : 0) + "-" + (number != null ? number : 0);
    }

    private String formatCellLabel(Integer drum, Integer column, Integer number) {
        StringBuilder sb = new StringBuilder();
        if (drum != null) sb.append("Барабан ").append(drum).append(", ");
        if (column != null) sb.append("Колонка ").append(column).append(", ");
        if (number != null) sb.append("Ячейка ").append(number);
        return sb.length() > 0 ? sb.toString() : "—";
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

        logTemplateEvent(
                template.getUid(),
                cell.getUid(),
                "CREATE",
                "Создание ячейки " + formatCellLabel(cell.getDrumNumber(), cell.getColumnNumber(), cell.getNumberCell()),
                null,
                null,
                null,
                "Через карточку"
        );

        return toCellDto(cell);
    }

    @Transactional
    public CellDto updateCell(UUID cellUid, CellRequest request) {
        RegCells cell = regCellsRepository.findById(cellUid)
                .orElseThrow(() -> new RuntimeException("Ячейка не найдена: " + cellUid));

        DocPattern template = cell.getDocPattern();
        String cellLabel = formatCellLabel(cell.getDrumNumber(), cell.getColumnNumber(), cell.getNumberCell());

        // === Назначение ===
        String oldAssignUid = cell.getCellAssignment() != null ? cell.getCellAssignment().getUid().toString() : null;
        String newAssignUid = request.getCellAssignmentUid() != null ? request.getCellAssignmentUid().toString() : null;
        if (!Objects.equals(oldAssignUid, newAssignUid)) {
            String oldName = cell.getCellAssignment() != null ? cell.getCellAssignment().getName() : null;
            SprCellAssignment newAssign = null;
            if (request.getCellAssignmentUid() != null) {
                newAssign = cellAssignmentRepository.findById(request.getCellAssignmentUid())
                        .orElseThrow(() -> new RuntimeException("Назначение не найдено: " + request.getCellAssignmentUid()));
            }
            logTemplateEvent(template.getUid(), cell.getUid(), "UPDATE",
                    "Ячейка " + cellLabel + ": Назначение '" + (oldName != null ? oldName : "—") + "' → '" + (newAssign != null ? newAssign.getName() : "—") + "'",
                    "Назначение", oldName, newAssign != null ? newAssign.getName() : null, "Через карточку");
            cell.setCellAssignment(newAssign);
        } else {
            cell.setCellAssignment(cell.getCellAssignment());
        }

        // === Материал ===
        String oldMatUid = cell.getMaterial() != null ? cell.getMaterial().getUid().toString() : null;
        String newMatUid = request.getMaterialUid() != null ? request.getMaterialUid().toString() : null;
        if (!Objects.equals(oldMatUid, newMatUid)) {
            String oldName = cell.getMaterial() != null ? cell.getMaterial().getNameMaterial() : null;
            SprMaterial newMat = null;
            if (request.getMaterialUid() != null) {
                newMat = materialRepository.findById(request.getMaterialUid())
                        .orElseThrow(() -> new RuntimeException("Материал не найден: " + request.getMaterialUid()));
            }
            logTemplateEvent(template.getUid(), cell.getUid(), "UPDATE",
                    "Ячейка " + cellLabel + ": Номенклатура '" + (oldName != null ? oldName : "—") + "' → '" + (newMat != null ? newMat.getNameMaterial() : "—") + "'",
                    "Номенклатура", oldName, newMat != null ? newMat.getNameMaterial() : null, "Через карточку");
            cell.setMaterial(newMat);
        }

        // === Количество ===
        if (!Objects.equals(cell.getQuantity(), request.getQuantity())) {
            logTemplateEvent(template.getUid(), cell.getUid(), "UPDATE",
                    "Ячейка " + cellLabel + ": Количество " + (cell.getQuantity() != null ? cell.getQuantity() : "—") + " → " + (request.getQuantity() != null ? request.getQuantity() : "—"),
                    "Количество",
                    cell.getQuantity() != null ? String.valueOf(cell.getQuantity()) : null,
                    request.getQuantity() != null ? String.valueOf(request.getQuantity()) : null,
                    "Через карточку");
            cell.setQuantity(request.getQuantity());
        }

        // === Возврат в ячейку ===
        if (request.getReturnToThisCell() != null) {
            boolean oldV = cell.getReturnToThisCell() != null && cell.getReturnToThisCell();
            boolean newV = request.getReturnToThisCell();
            if (oldV != newV) {
                logTemplateEvent(template.getUid(), cell.getUid(), "UPDATE",
                        "Ячейка " + cellLabel + ": Возврат в ячейку " + (oldV ? "Да" : "Нет") + " → " + (newV ? "Да" : "Нет"),
                        "Возврат в ячейку", oldV ? "Да" : "Нет", newV ? "Да" : "Нет", "Через карточку");
            }
            cell.setReturnToThisCell(request.getReturnToThisCell());
        }

        // === Индивидуальная ячейка ===
        if (request.getIsIndividual() != null) {
            boolean oldV = cell.getIsIndividual() != null && cell.getIsIndividual();
            boolean newV = request.getIsIndividual();
            if (oldV != newV) {
                logTemplateEvent(template.getUid(), cell.getUid(), "UPDATE",
                        "Ячейка " + cellLabel + ": Индивидуальная " + (oldV ? "Да" : "Нет") + " → " + (newV ? "Да" : "Нет"),
                        "Индивидуальная ячейка", oldV ? "Да" : "Нет", newV ? "Да" : "Нет", "Через карточку");
            }
            cell.setIsIndividual(request.getIsIndividual());
        }

        regCellsRepository.save(cell);
        recalcTemplateStats(template);

        return toCellDto(cell);
    }

    @Transactional
    public void clearCell(UUID cellUid) {
        RegCells cell = regCellsRepository.findById(cellUid)
                .orElseThrow(() -> new RuntimeException("Ячейка не найдена: " + cellUid));

        DocPattern template = cell.getDocPattern();
        String cellLabel = formatCellLabel(cell.getDrumNumber(), cell.getColumnNumber(), cell.getNumberCell());

        // Снимок для истории
        String oldAssignName = cell.getCellAssignment() != null ? cell.getCellAssignment().getName() : null;
        String oldMatName = cell.getMaterial() != null ? cell.getMaterial().getNameMaterial() : null;
        Integer oldQty = cell.getQuantity();

        cell.clear();
        regCellsRepository.save(cell);
        recalcTemplateStats(template);

        logTemplateEvent(
                template.getUid(),
                cell.getUid(),
                "CLEAR",
                "Очистка ячейки " + cellLabel +
                        " (было: Назначение='" + (oldAssignName != null ? oldAssignName : "—") +
                        "', Номенклатура='" + (oldMatName != null ? oldMatName : "—") +
                        "', Количество=" + (oldQty != null ? oldQty : "—") + ")",
                null,
                null,
                null,
                "Через карточку"
        );
    }

    @Transactional
    public void clearBatchCells(ClearBatchRequest request) {
        List<RegCells> cells = regCellsRepository.findAllById(request.getCellUids());
        DocPattern template = null;

        for (RegCells cell : cells) {
            if (template == null) {
                template = cell.getDocPattern();
            }
            String cellLabel = formatCellLabel(cell.getDrumNumber(), cell.getColumnNumber(), cell.getNumberCell());

            String oldAssignName = cell.getCellAssignment() != null ? cell.getCellAssignment().getName() : null;
            String oldMatName = cell.getMaterial() != null ? cell.getMaterial().getNameMaterial() : null;

            cell.clear();
            regCellsRepository.save(cell);

            logTemplateEvent(
                    cell.getDocPattern().getUid(),
                    cell.getUid(),
                    "CLEAR",
                    "Массовая очистка ячейки " + cellLabel +
                            " (было: Назначение='" + (oldAssignName != null ? oldAssignName : "—") +
                            "', Номенклатура='" + (oldMatName != null ? oldMatName : "—") + "')",
                    null,
                    null,
                    null,
                    "Через карточку"
            );
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

    // ==================== ИСТОРИЯ ИЗМЕНЕНИЙ ====================

    public List<TemplateEventLogDto> getEvents(UUID templateUid) {
        return templateEventLogRepository
                .findByTemplateUidOrderByCreatedAtDesc(templateUid)
                .stream()
                .map(this::toEventDto)
                .collect(Collectors.toList());
    }

    public List<TemplateEventLogDto> getAllEvents() {
        return templateEventLogRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toEventDto)
                .collect(Collectors.toList());
    }

    private void logTemplateEvent(
            UUID templateUid,
            UUID cellUid,
            String eventType,
            String description,
            String fieldName,
            String oldValue,
            String newValue,
            String source
    ) {
        TemplateEventLog log = TemplateEventLog.builder()
                .uid(UUID.randomUUID())
                .templateUid(templateUid)
                .cellUid(cellUid)
                .eventType(eventType)
                .eventDescription(description)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .author(userService.getCurrentUsername())
                .source(source != null ? source : "Через карточку")
                .createdAt(LocalDateTime.now())
                .build();
        templateEventLogRepository.save(log);
    }

    private TemplateEventLogDto toEventDto(TemplateEventLog e) {
        return TemplateEventLogDto.builder()
                .uid(e.getUid())
                .templateUid(e.getTemplateUid())
                .cellUid(e.getCellUid())
                .eventType(e.getEventType())
                .eventDescription(e.getEventDescription())
                .fieldName(e.getFieldName())
                .oldValue(e.getOldValue())
                .newValue(e.getNewValue())
                .author(e.getAuthor())
                .source(e.getSource())
                .createdAt(e.getCreatedAt())
                .build();
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
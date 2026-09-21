// TemplateService.java — ПОЛНЫЙ ФАЙЛ (copyTemplate проставляет createdAt/updatedAt)
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.*;
import com.example.dinamika_back.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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
    private final SprTypeMaterialRepository typeMaterialRepository;

    // ==================== КАТЕГОРИИ ====================

    public List<TemplateCategoryDto> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(this::toCategoryDto)
                .collect(Collectors.toList());
    }

    public TemplateCategoryDto getCategoryById(Long id) {
        TemplateCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Категория не найдена: " + id));
        return toCategoryDto(category);
    }

    @Transactional
    public TemplateCategoryDto createCategory(TemplateCategoryRequest request) {
        TemplateCategory category = new TemplateCategory();
        category.setUid(UUID.randomUUID());
        category.setName(request.getName());
        categoryRepository.save(category);
        return toCategoryDto(category);
    }

    @Transactional
    public TemplateCategoryDto updateCategory(Long id, TemplateCategoryRequest request) {
        TemplateCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Категория не найдена: " + id));
        category.setName(request.getName());
        categoryRepository.save(category);
        return toCategoryDto(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        if (docPatternRepository.findByCategoryId(id).size() > 0) {
            throw new RuntimeException("Нельзя удалить категорию, в которой есть шаблоны");
        }
        categoryRepository.deleteById(id);
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

        // === Копирование ячеек источника ===
        List<RegCells> sourceCells = regCellsRepository.findByDocPatternUid(source.getUid());
        for (RegCells src : sourceCells) {
            RegCells newCell = new RegCells();
            newCell.setUid(UUID.randomUUID());
            newCell.setDocPattern(copy);
            newCell.setNumberCell(src.getNumberCell());
            newCell.setColumnNumber(src.getColumnNumber());
            newCell.setDrumNumber(src.getDrumNumber());
            newCell.setMaterial(src.getMaterial());
            newCell.setQuantity(src.getQuantity());
            newCell.setTypeMain(src.getTypeMain());
            newCell.setPurposeMaterial(src.getPurposeMaterial());
            newCell.setPurposeSgd(src.getPurposeSgd());
            newCell.setMaxQuantity(src.getMaxQuantity());
            newCell.setDimensions(src.getDimensions());
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
                cell.setPurposeMaterial(item.getPurposeMaterial());
                cell.setPurposeSgd(item.getPurposeSgd());
                cell.setMaxQuantity(item.getMaxQuantity());
                cell.setDimensions(item.getDimensions());

                if (item.getMaterialUid() != null) {
                    SprMaterial material = materialRepository.findById(item.getMaterialUid())
                            .orElseThrow(() -> new RuntimeException("Материал не найден: " + item.getMaterialUid()));
                    cell.setMaterial(material);
                }
                if (item.getTypeMainUid() != null) {
                    SprTypeMaterial typeMain = typeMaterialRepository.findById(item.getTypeMainUid())
                            .orElseThrow(() -> new RuntimeException("Тип материала не найден: " + item.getTypeMainUid()));
                    cell.setTypeMain(typeMain);
                }

                regCellsRepository.save(cell);
            }
        }

        recalcTemplateStats(template);
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

        if (request.getMaterialUid() != null) {
            SprMaterial material = materialRepository.findById(request.getMaterialUid())
                    .orElseThrow(() -> new RuntimeException("Материал не найден: " + request.getMaterialUid()));
            cell.setMaterial(material);
        }

        cell.setPurposeMaterial(request.getPurposeMaterial());
        cell.setPurposeSgd(request.getPurposeSgd());
        cell.setMaxQuantity(request.getMaxQuantity());

        if (request.getTypeMainUid() != null) {
            SprTypeMaterial typeMain = typeMaterialRepository.findById(request.getTypeMainUid())
                    .orElseThrow(() -> new RuntimeException("Тип материала не найден: " + request.getTypeMainUid()));
            cell.setTypeMain(typeMain);
        }

        regCellsRepository.save(cell);
        recalcTemplateStats(template);

        return toCellDto(cell);
    }

    @Transactional
    public CellDto updateCell(UUID cellUid, CellRequest request) {
        RegCells cell = regCellsRepository.findById(cellUid)
                .orElseThrow(() -> new RuntimeException("Ячейка не найдена: " + cellUid));

        if (request.getMaterialUid() != null) {
            SprMaterial material = materialRepository.findById(request.getMaterialUid())
                    .orElseThrow(() -> new RuntimeException("Материал не найден: " + request.getMaterialUid()));
            cell.setMaterial(material);
        } else {
            cell.setMaterial(null);
        }

        cell.setQuantity(request.getQuantity());
        cell.setPurposeMaterial(request.getPurposeMaterial());
        cell.setPurposeSgd(request.getPurposeSgd());
        cell.setMaxQuantity(request.getMaxQuantity());
        cell.setDimensions(request.getDimensions());

        if (request.getTypeMainUid() != null) {
            SprTypeMaterial typeMain = typeMaterialRepository.findById(request.getTypeMainUid())
                    .orElseThrow(() -> new RuntimeException("Тип материала не найден: " + request.getTypeMainUid()));
            cell.setTypeMain(typeMain);
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
        return CellDto.builder()
                .uid(cell.getUid())
                .numberCell(cell.getNumberCell())
                .columnNumber(cell.getColumnNumber())
                .drumNumber(cell.getDrumNumber())
                .materialUid(cell.getMaterial() != null ? cell.getMaterial().getUid() : null)
                .materialName(cell.getMaterial() != null ? cell.getMaterial().getNameMaterial() : null)
                .materialArticle(cell.getMaterial() != null ? cell.getMaterial().getArticle() : null)
                .quantity(cell.getQuantity())
                .typeMainUid(cell.getTypeMain() != null ? cell.getTypeMain().getUid() : null)
                .typeMainName(cell.getTypeMain() != null ? cell.getTypeMain().getTypeName() : null)
                .purposeMaterial(cell.getPurposeMaterial())
                .purposeSgd(cell.getPurposeSgd())
                .maxQuantity(cell.getMaxQuantity())
                .dimensions(cell.getDimensions())
                .build();
    }
}
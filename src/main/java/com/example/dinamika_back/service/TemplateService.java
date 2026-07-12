// TemplateService.java — ПОЛНЫЙ ФАЙЛ
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.DocPattern;
import com.example.dinamika_back.model.Station;
import com.example.dinamika_back.model.StationConfiguration;
import com.example.dinamika_back.model.TemplateCategory;
import com.example.dinamika_back.repository.DocPatternRepository;
import com.example.dinamika_back.repository.StationConfigurationRepository;
import com.example.dinamika_back.repository.StationRepository;
import com.example.dinamika_back.repository.TemplateCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        DocPattern copy = new DocPattern();
        copy.setUid(UUID.randomUUID());
        copy.setNamePattern(source.getNamePattern() + " (копия)");
        copy.setNumber(nextNumber);
        copy.setConfiguration(source.getConfiguration());
        copy.setStationConfiguration(source.getStationConfiguration());

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
        return toTemplateDto(copy);
    }

    // ==================== СТАНЦИИ ШАБЛОНА ====================

    public List<String> getTemplateStations(UUID templateUid) {
        List<Station> stations = stationRepository.findByActiveTemplateUid(templateUid);
        return stations.stream()
                .map(Station::getName)
                .collect(Collectors.toList());
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
}
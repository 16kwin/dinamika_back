// ModelService.java — ПОЛНЫЙ ФАЙЛ (добавлено логирование в номенклатуру)
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
public class ModelService {

    private final SprModelOfBrandRepository modelRepository;
    private final SprBrandRepository brandRepository;
    private final SprManufacturerRepository manufacturerRepository;
    private final SprMaterialRepository materialRepository;
    private final ModelEventLogRepository eventLogRepository;
    private final ModelColumnSettingsService columnSettingsService;
    private final UserService userService;
    private final NomenclatureService nomenclatureService;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of("name", "description", "brandName", "manufacturerName");
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("name", "brandName", "manufacturerName"));

    // ==================== GET ALL WITH SETTINGS ====================

    public ModelListResponse getAllWithSettings(Integer userId) {
        String columnsJson = columnSettingsService.getColumnsJson(userId);
        Set<String> visibleColumns = new LinkedHashSet<>(ALL_COLUMNS_ORDER);
        Map<String, Double> columnWidths = new HashMap<>();
        Set<String> requiredColumns = new LinkedHashSet<>(REQUIRED_COLUMNS);

        if (columnsJson != null && !columnsJson.isEmpty() && !"{}".equals(columnsJson)) {
            parseColumnSettings(columnsJson, visibleColumns, columnWidths, requiredColumns);
        }

        List<String> orderedColumns = ALL_COLUMNS_ORDER.stream()
                .filter(visibleColumns::contains)
                .collect(Collectors.toList());

        List<Map<String, Object>> data = modelRepository.findAll().stream()
                .map(m -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uid", m.getUid().toString());
                    row.put("name", m.getName());
                    row.put("description", m.getDescription());
                    row.put("brandUid", m.getBrand() != null ? m.getBrand().getUid().toString() : null);
                    row.put("brandName", m.getBrand() != null ? m.getBrand().getName() : null);
                    row.put("manufacturerUid", m.getManufacturer() != null ? m.getManufacturer().getUid().toString() : null);
                    row.put("manufacturerName", m.getManufacturer() != null ? m.getManufacturer().getName() : null);
                    return row;
                })
                .collect(Collectors.toList());

        ModelListResponse response = new ModelListResponse();
        response.setColumns(orderedColumns);
        response.setData(data);
        response.setColumnWidths(columnWidths);
        response.setRequiredColumns(new ArrayList<>(requiredColumns));
        return response;
    }

    private void parseColumnSettings(String json, Set<String> visibleColumns, Map<String, Double> columnWidths, Set<String> requiredColumns) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            
            if (map.isEmpty()) {
                visibleColumns.clear();
                visibleColumns.addAll(ALL_COLUMNS_ORDER);
                return;
            }
            
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
            
            if (visibleColumns.isEmpty()) {
                visibleColumns.addAll(ALL_COLUMNS_ORDER);
            }
        } catch (Exception e) {
            visibleColumns.clear();
            visibleColumns.addAll(ALL_COLUMNS_ORDER);
        }
    }

    // ==================== CRUD ====================

    @Transactional
    public Map<String, Object> create(CreateModelRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Наименование модели обязательно");
        }

        SprModelOfBrand model = new SprModelOfBrand();
        model.setUid(UUID.randomUUID());
        model.setName(request.getName());
        model.setDescription(request.getDescription());

        if (request.getBrandUid() != null) {
            model.setBrand(brandRepository.findById(request.getBrandUid())
                    .orElseThrow(() -> new RuntimeException("Бренд не найден: " + request.getBrandUid())));
        }

        if (request.getManufacturerUid() != null) {
            model.setManufacturer(manufacturerRepository.findById(request.getManufacturerUid())
                    .orElseThrow(() -> new RuntimeException("Производитель не найден: " + request.getManufacturerUid())));
        }

        model = modelRepository.save(model);

        String author = userService.getCurrentUsername();
        logEvent(model.getUid(), "CREATE", "Создание модели: '" + model.getName() + "'",
                null, null, null, author);

        return toRowData(model);
    }

    @Transactional
    public Map<String, Object> update(UUID uid, UpdateModelRequest request) {
        SprModelOfBrand model = modelRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Модель не найдена: " + uid));

        String author = userService.getCurrentUsername();

        if (request.getName() != null && !request.getName().isBlank()) {
            if (!model.getName().equals(request.getName())) {
                String oldName = model.getName();
                logEvent(uid, "UPDATE",
                        "'" + oldName + "': Значение поля 'Наименование' изменено с '" + oldName + "' на '" + request.getName() + "'",
                        "Наименование", oldName, request.getName(), author);
                model.setName(request.getName());

                // Логируем в номенклатуру
                List<SprMaterial> relatedMaterials = materialRepository.findByModelOfBrandUid(uid);
                for (SprMaterial material : relatedMaterials) {
                    nomenclatureService.logEventFromReference(material.getUid(), "Модель", oldName, request.getName(), author, "справочник 'Модели'");
                }
            }
        }

        if (request.getDescription() != null && !Objects.equals(request.getDescription(), model.getDescription())) {
            String oldVal = model.getDescription() != null ? model.getDescription() : "null";
            String newVal = request.getDescription() != null ? request.getDescription() : "null";
            logEvent(uid, "UPDATE",
                    "'" + model.getName() + "': Значение поля 'Описание' изменено с '" + oldVal + "' на '" + newVal + "'",
                    "Описание", oldVal, newVal, author);
            model.setDescription(request.getDescription());
        }

        if (request.getBrandUid() != null) {
            SprBrand newBrand = brandRepository.findById(request.getBrandUid())
                    .orElseThrow(() -> new RuntimeException("Бренд не найден: " + request.getBrandUid()));
            String oldBrandName = model.getBrand() != null ? model.getBrand().getName() : "null";
            if (!newBrand.getName().equals(oldBrandName)) {
                logEvent(uid, "UPDATE",
                        "'" + model.getName() + "': Значение поля 'Бренд' изменено с '" + oldBrandName + "' на '" + newBrand.getName() + "'",
                        "Бренд", oldBrandName, newBrand.getName(), author);
                model.setBrand(newBrand);
            }
        }

        if (request.getManufacturerUid() != null) {
            SprManufacturer newManufacturer = manufacturerRepository.findById(request.getManufacturerUid())
                    .orElseThrow(() -> new RuntimeException("Производитель не найден: " + request.getManufacturerUid()));
            String oldManufacturerName = model.getManufacturer() != null ? model.getManufacturer().getName() : "null";
            if (!newManufacturer.getName().equals(oldManufacturerName)) {
                logEvent(uid, "UPDATE",
                        "'" + model.getName() + "': Значение поля 'Производитель' изменено с '" + oldManufacturerName + "' на '" + newManufacturer.getName() + "'",
                        "Производитель", oldManufacturerName, newManufacturer.getName(), author);
                model.setManufacturer(newManufacturer);
            }
        }

        model = modelRepository.save(model);
        return toRowData(model);
    }

    @Transactional
    public void delete(UUID uid) {
        SprModelOfBrand model = modelRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Модель не найдена: " + uid));

        String author = userService.getCurrentUsername();

        // Логируем в номенклатуру и обнуляем привязку
        List<SprMaterial> relatedMaterials = materialRepository.findByModelOfBrandUid(uid);
        for (SprMaterial material : relatedMaterials) {
            nomenclatureService.logEventFromReference(material.getUid(), "Модель", model.getName(), null, author, "справочник 'Модели'");
            material.setModelOfBrand(null);
            materialRepository.save(material);
        }

        logEvent(uid, "DELETE", "Удаление модели: '" + model.getName() + "'",
                null, model.getName(), null, author);

        modelRepository.delete(model);
    }

    // ==================== EVENTS ====================

    public List<ModelEventLogDto> getEvents(UUID modelUid) {
        return eventLogRepository.findByModelUidOrderByCreatedAtDesc(modelUid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<ModelEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PUBLIC LOGGING ====================

    @Transactional
    public void logEventFromBrand(UUID modelUid, String eventType, String description,
                                   String fieldName, String oldValue, String newValue, String author) {
        ModelEventLog log = ModelEventLog.builder()
                .uid(UUID.randomUUID())
                .modelUid(modelUid)
                .eventType(eventType)
                .eventDescription(description)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .author(author)
                .source("Через справочник 'Бренды'")
                .createdAt(LocalDateTime.now())
                .build();
        eventLogRepository.save(log);
    }

    @Transactional
    public void logEventFromManufacturer(UUID modelUid, String eventType, String description,
                                          String fieldName, String oldValue, String newValue, String author) {
        ModelEventLog log = ModelEventLog.builder()
                .uid(UUID.randomUUID())
                .modelUid(modelUid)
                .eventType(eventType)
                .eventDescription(description)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .author(author)
                .source("Через справочник 'Производители'")
                .createdAt(LocalDateTime.now())
                .build();
        eventLogRepository.save(log);
    }

    // ==================== PRIVATE ====================

    private void logEvent(UUID modelUid, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        ModelEventLog log = ModelEventLog.builder()
                .uid(UUID.randomUUID())
                .modelUid(modelUid)
                .eventType(eventType)
                .eventDescription(description)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .author(author)
                .source("Через карточку")
                .createdAt(LocalDateTime.now())
                .build();
        eventLogRepository.save(log);
    }

    private ModelEventLogDto toEventDTO(ModelEventLog e) {
        return ModelEventLogDto.builder()
                .uid(e.getUid())
                .modelUid(e.getModelUid())
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

    private Map<String, Object> toRowData(SprModelOfBrand m) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uid", m.getUid().toString());
        row.put("name", m.getName());
        row.put("description", m.getDescription());
        row.put("brandUid", m.getBrand() != null ? m.getBrand().getUid().toString() : null);
        row.put("brandName", m.getBrand() != null ? m.getBrand().getName() : null);
        row.put("manufacturerUid", m.getManufacturer() != null ? m.getManufacturer().getUid().toString() : null);
        row.put("manufacturerName", m.getManufacturer() != null ? m.getManufacturer().getName() : null);
        return row;
    }
}
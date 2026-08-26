// BrandService.java — ПОЛНЫЙ ФАЙЛ (добавлено логирование в номенклатуру)
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
public class BrandService {

    private final SprBrandRepository brandRepository;
    private final SprManufacturerRepository manufacturerRepository;
    private final SprModelOfBrandRepository modelOfBrandRepository;
    private final SprMaterialRepository materialRepository;
    private final BrandEventLogRepository eventLogRepository;
    private final ModelEventLogRepository modelEventLogRepository;
    private final BrandColumnSettingsService columnSettingsService;
    private final UserService userService;
    private final NomenclatureService nomenclatureService;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of("name", "description", "manufacturerName");
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("name", "manufacturerName"));

    // ==================== GET ALL WITH SETTINGS ====================

    public BrandListResponse getAllWithSettings(Integer userId) {
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

        List<Map<String, Object>> data = brandRepository.findAll().stream()
                .map(b -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uid", b.getUid().toString());
                    row.put("name", b.getName());
                    row.put("description", b.getDescription());
                    row.put("manufacturerUid", b.getManufacturer() != null ? b.getManufacturer().getUid().toString() : null);
                    row.put("manufacturerName", b.getManufacturer() != null ? b.getManufacturer().getName() : null);
                    return row;
                })
                .collect(Collectors.toList());

        BrandListResponse response = new BrandListResponse();
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
    public Map<String, Object> create(CreateBrandRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Наименование бренда обязательно");
        }

        SprBrand brand = new SprBrand();
        brand.setUid(UUID.randomUUID());
        brand.setName(request.getName());
        brand.setDescription(request.getDescription());

        if (request.getManufacturerUid() != null) {
            brand.setManufacturer(manufacturerRepository.findById(request.getManufacturerUid())
                    .orElseThrow(() -> new RuntimeException("Производитель не найден: " + request.getManufacturerUid())));
        }

        brand = brandRepository.save(brand);

        String author = userService.getCurrentUsername();
        logEvent(brand.getUid(), "CREATE", "Создание бренда: '" + brand.getName() + "'",
                null, null, null, author);

        return toRowData(brand);
    }

    @Transactional
    public Map<String, Object> update(UUID uid, UpdateBrandRequest request) {
        SprBrand brand = brandRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Бренд не найден: " + uid));

        String author = userService.getCurrentUsername();

        if (request.getName() != null && !request.getName().isBlank()) {
            if (!brand.getName().equals(request.getName())) {
                String oldName = brand.getName();
                logEvent(uid, "UPDATE",
                        "'" + oldName + "': Значение поля 'Наименование' изменено с '" + oldName + "' на '" + request.getName() + "'",
                        "Наименование", oldName, request.getName(), author);
                brand.setName(request.getName());

                // Логируем в модели брендов
                List<SprModelOfBrand> relatedModels = modelOfBrandRepository.findByBrandUid(uid);
                for (SprModelOfBrand model : relatedModels) {
                    logModelEvent(model.getUid(), "UPDATE",
                            "'" + model.getName() + "': Значение поля 'Бренд' изменено с '" + oldName + "' на '" + request.getName() + "' через справочник 'Бренды'",
                            "Бренд", oldName, request.getName(), author);
                }

                // Логируем в номенклатуру
                List<SprMaterial> relatedMaterials = materialRepository.findByBrandUid(uid);
                for (SprMaterial material : relatedMaterials) {
                    nomenclatureService.logEventFromReference(material.getUid(), "Бренд", oldName, request.getName(), author, "справочник 'Бренды'");
                }
            }
        }

        if (request.getDescription() != null && !Objects.equals(request.getDescription(), brand.getDescription())) {
            String oldVal = brand.getDescription() != null ? brand.getDescription() : "null";
            String newVal = request.getDescription() != null ? request.getDescription() : "null";
            logEvent(uid, "UPDATE",
                    "'" + brand.getName() + "': Значение поля 'Описание' изменено с '" + oldVal + "' на '" + newVal + "'",
                    "Описание", oldVal, newVal, author);
            brand.setDescription(request.getDescription());
        }

        if (request.getManufacturerUid() != null) {
            SprManufacturer newManufacturer = manufacturerRepository.findById(request.getManufacturerUid())
                    .orElseThrow(() -> new RuntimeException("Производитель не найден: " + request.getManufacturerUid()));
            String oldManufacturerName = brand.getManufacturer() != null ? brand.getManufacturer().getName() : "null";
            if (!newManufacturer.getName().equals(oldManufacturerName)) {
                logEvent(uid, "UPDATE",
                        "'" + brand.getName() + "': Значение поля 'Производитель' изменено с '" + oldManufacturerName + "' на '" + newManufacturer.getName() + "'",
                        "Производитель", oldManufacturerName, newManufacturer.getName(), author);
                brand.setManufacturer(newManufacturer);

                // Логируем в модели брендов
                List<SprModelOfBrand> relatedModels = modelOfBrandRepository.findByBrandUid(uid);
                for (SprModelOfBrand model : relatedModels) {
                    String oldModelManufacturerName = model.getManufacturer() != null ? model.getManufacturer().getName() : "null";
                    logModelEvent(model.getUid(), "UPDATE",
                            "'" + model.getName() + "': Значение поля 'Производитель' изменено с '" + oldModelManufacturerName + "' на '" + newManufacturer.getName() + "' через справочник 'Бренды'",
                            "Производитель", oldModelManufacturerName, newManufacturer.getName(), author);
                    model.setManufacturer(newManufacturer);
                    modelOfBrandRepository.save(model);
                }
            }
        }

        brand = brandRepository.save(brand);
        return toRowData(brand);
    }

    @Transactional
    public void delete(UUID uid) {
        SprBrand brand = brandRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Бренд не найден: " + uid));

        String author = userService.getCurrentUsername();

        // Логируем в модели брендов и обнуляем привязку
        List<SprModelOfBrand> relatedModels = modelOfBrandRepository.findByBrandUid(uid);
        for (SprModelOfBrand model : relatedModels) {
            logModelEvent(model.getUid(), "UPDATE",
                    "'" + model.getName() + "': Значение поля 'Бренд' изменено с '" + brand.getName() + "' на 'null' через справочник 'Бренды'",
                    "Бренд", brand.getName(), null, author);
            model.setBrand(null);
            modelOfBrandRepository.save(model);
        }

        // Логируем в номенклатуру и обнуляем привязку
        List<SprMaterial> relatedMaterials = materialRepository.findByBrandUid(uid);
        for (SprMaterial material : relatedMaterials) {
            nomenclatureService.logEventFromReference(material.getUid(), "Бренд", brand.getName(), null, author, "справочник 'Бренды'");
            material.setBrand(null);
            materialRepository.save(material);
        }

        logEvent(uid, "DELETE", "Удаление бренда: '" + brand.getName() + "'",
                null, brand.getName(), null, author);

        brandRepository.delete(brand);
    }

    // ==================== EVENTS ====================

    public List<BrandEventLogDto> getEvents(UUID brandUid) {
        return eventLogRepository.findByBrandUidOrderByCreatedAtDesc(brandUid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<BrandEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE ====================

    private void logEvent(UUID brandUid, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        BrandEventLog log = BrandEventLog.builder()
                .uid(UUID.randomUUID())
                .brandUid(brandUid)
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

    private void logModelEvent(UUID modelUid, String eventType, String description,
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
        modelEventLogRepository.save(log);
    }

    private BrandEventLogDto toEventDTO(BrandEventLog e) {
        return BrandEventLogDto.builder()
                .uid(e.getUid())
                .brandUid(e.getBrandUid())
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

    private Map<String, Object> toRowData(SprBrand b) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uid", b.getUid().toString());
        row.put("name", b.getName());
        row.put("description", b.getDescription());
        row.put("manufacturerUid", b.getManufacturer() != null ? b.getManufacturer().getUid().toString() : null);
        row.put("manufacturerName", b.getManufacturer() != null ? b.getManufacturer().getName() : null);
        return row;
    }
}
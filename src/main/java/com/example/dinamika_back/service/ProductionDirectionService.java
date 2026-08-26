package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.SprManufacturer;
import com.example.dinamika_back.model.SprProductionDirection;
import com.example.dinamika_back.model.ProductionDirectionEventLog;
import com.example.dinamika_back.model.ManufacturerEventLog;
import com.example.dinamika_back.repository.SprProductionDirectionRepository;
import com.example.dinamika_back.repository.SprManufacturerRepository;
import com.example.dinamika_back.repository.ProductionDirectionEventLogRepository;
import com.example.dinamika_back.repository.ManufacturerEventLogRepository;
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
public class ProductionDirectionService {

    private final SprProductionDirectionRepository directionRepository;
    private final SprManufacturerRepository manufacturerRepository;
    private final ProductionDirectionEventLogRepository eventLogRepository;
    private final ManufacturerEventLogRepository manufacturerEventLogRepository;
    private final ProductionDirectionColumnSettingsService columnSettingsService;
    private final UserService userService;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of("name");
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("name"));

    // ==================== GET ALL WITH SETTINGS ====================

    public ProductionDirectionListResponse getAllWithSettings(Integer userId) {
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

        List<Map<String, Object>> data = directionRepository.findAll().stream()
                .map(d -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uid", d.getUid().toString());
                    row.put("name", d.getName());
                    return row;
                })
                .collect(Collectors.toList());

        ProductionDirectionListResponse response = new ProductionDirectionListResponse();
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
    public Map<String, Object> create(CreateProductionDirectionRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Наименование направления производства обязательно");
        }

        SprProductionDirection direction = new SprProductionDirection();
        direction.setUid(UUID.randomUUID());
        direction.setName(request.getName());
        direction = directionRepository.save(direction);

        String author = userService.getCurrentUsername();
        logEvent(direction.getUid(), "CREATE", "Создание направления производства: '" + direction.getName() + "'",
                null, null, null, author);

        return toRowData(direction);
    }

    @Transactional
    public Map<String, Object> update(UUID uid, UpdateProductionDirectionRequest request) {
        SprProductionDirection direction = directionRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Направление производства не найдено: " + uid));

        String author = userService.getCurrentUsername();

        if (request.getName() != null && !request.getName().isBlank()) {
            if (!direction.getName().equals(request.getName())) {
                String oldName = direction.getName();
                logEvent(uid, "UPDATE",
                        "'" + oldName + "': Значение поля 'Наименование' изменено с '" + oldName + "' на '" + request.getName() + "'",
                        "Наименование", oldName, request.getName(), author);
                direction.setName(request.getName());

                // Логируем в связанных производителей
                List<SprManufacturer> relatedManufacturers = manufacturerRepository.findByDirectionUid(uid);
                for (SprManufacturer manufacturer : relatedManufacturers) {
                    logManufacturerEvent(manufacturer.getUid(), "UPDATE",
                            "'" + manufacturer.getName() + "': Значение поля 'Направление производства' изменено с '" + oldName + "' на '" + request.getName() + "' через справочник 'Направления производства'",
                            "Направление производства", oldName, request.getName(), author);
                }
            }
        }

        direction = directionRepository.save(direction);
        return toRowData(direction);
    }

    @Transactional
    public void delete(UUID uid) {
        SprProductionDirection direction = directionRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Направление производства не найдено: " + uid));

        String author = userService.getCurrentUsername();

        // Логируем в связанных производителей и обнуляем привязку
        List<SprManufacturer> relatedManufacturers = manufacturerRepository.findByDirectionUid(uid);
        for (SprManufacturer manufacturer : relatedManufacturers) {
            logManufacturerEvent(manufacturer.getUid(), "UPDATE",
                    "'" + manufacturer.getName() + "': Значение поля 'Направление производства' изменено с '" + direction.getName() + "' на 'null' через справочник 'Направления производства'",
                    "Направление производства", direction.getName(), null, author);
            manufacturer.setDirection(null);
            manufacturerRepository.save(manufacturer);
        }

        logEvent(uid, "DELETE", "Удаление направления производства: '" + direction.getName() + "'",
                null, direction.getName(), null, author);

        directionRepository.delete(direction);
    }

    // ==================== EVENTS ====================

    public List<ProductionDirectionEventLogDto> getEvents(UUID directionUid) {
        return eventLogRepository.findByProductionDirectionUidOrderByCreatedAtDesc(directionUid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<ProductionDirectionEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE ====================

    private void logEvent(UUID directionUid, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        ProductionDirectionEventLog log = ProductionDirectionEventLog.builder()
                .uid(UUID.randomUUID())
                .productionDirectionUid(directionUid)
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

    private void logManufacturerEvent(UUID manufacturerUid, String eventType, String description,
                                      String fieldName, String oldValue, String newValue, String author) {
        ManufacturerEventLog log = ManufacturerEventLog.builder()
                .uid(UUID.randomUUID())
                .manufacturerUid(manufacturerUid)
                .eventType(eventType)
                .eventDescription(description)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .author(author)
                .source("Через справочник 'Направления производства'")
                .createdAt(LocalDateTime.now())
                .build();
        manufacturerEventLogRepository.save(log);
    }

    private ProductionDirectionEventLogDto toEventDTO(ProductionDirectionEventLog e) {
        return ProductionDirectionEventLogDto.builder()
                .uid(e.getUid())
                .productionDirectionUid(e.getProductionDirectionUid())
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

    private Map<String, Object> toRowData(SprProductionDirection d) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uid", d.getUid().toString());
        row.put("name", d.getName());
        return row;
    }
}
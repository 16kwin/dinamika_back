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
public class AttributeGroupService {

    private final SprAttributeGroupRepository attributeGroupRepository;
    private final SprTypeAttributesRepository typeAttributesRepository;
    private final SprMeasureRepository measureRepository;
    private final AttributeGroupEventLogRepository eventLogRepository;
    private final TypeAttributeEventLogRepository typeAttributeEventLogRepository;
    private final MeasureEventLogRepository measureEventLogRepository;
    private final AttributeGroupColumnSettingsService columnSettingsService;
    private final UserService userService;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of("name");
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("name"));

    // ==================== GET ALL WITH SETTINGS ====================

    public AttributeGroupListResponse getAllWithSettings(Integer userId) {
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

        List<Map<String, Object>> data = attributeGroupRepository.findAll().stream()
                .map(g -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uid", g.getUid().toString());
                    row.put("name", g.getName());
                    return row;
                })
                .collect(Collectors.toList());

        AttributeGroupListResponse response = new AttributeGroupListResponse();
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
    public Map<String, Object> create(CreateAttributeGroupRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Наименование группы характеристик обязательно");
        }

        SprAttributeGroup group = new SprAttributeGroup();
        group.setUid(UUID.randomUUID());
        group.setName(request.getName());
        group = attributeGroupRepository.save(group);

        String author = userService.getCurrentUsername();
        logEvent(group.getUid(), "CREATE", "Создание группы характеристик: '" + group.getName() + "'",
                null, null, null, author);

        return toRowData(group);
    }

    @Transactional
    public Map<String, Object> update(UUID uid, UpdateAttributeGroupRequest request) {
        SprAttributeGroup group = attributeGroupRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Группа характеристик не найдена: " + uid));

        String author = userService.getCurrentUsername();

        if (request.getName() != null && !request.getName().isBlank()) {
            if (!group.getName().equals(request.getName())) {
                String oldName = group.getName();
                logEvent(uid, "UPDATE",
                        "'" + oldName + "': Значение поля 'Наименование' изменено с '" + oldName + "' на '" + request.getName() + "'",
                        "Наименование", oldName, request.getName(), author);
                group.setName(request.getName());

                // Логируем в связанные виды характеристик
                List<SprTypeAttributes> relatedAttrs = typeAttributesRepository.findByGroupUid(uid);
                for (SprTypeAttributes attr : relatedAttrs) {
                    logTypeAttributeEvent(attr.getUid(), "UPDATE",
                            "'" + attr.getName() + "': Значение поля 'Группа характеристик' изменено с '" + oldName + "' на '" + request.getName() + "' через справочник 'Группы характеристик'",
                            "Группа характеристик", oldName, request.getName(), author);
                }

                // Логируем в связанные единицы измерения
                List<SprMeasure> relatedMeasures = measureRepository.findByGroupUid(uid);
                for (SprMeasure measure : relatedMeasures) {
                    logMeasureEvent(measure.getUid(), "UPDATE",
                            "'" + measure.getName() + "': Значение поля 'Группа характеристик' изменено с '" + oldName + "' на '" + request.getName() + "' через справочник 'Группы характеристик'",
                            "Группа характеристик", oldName, request.getName(), author);
                }
            }
        }

        group = attributeGroupRepository.save(group);
        return toRowData(group);
    }

    @Transactional
    public void delete(UUID uid) {
        SprAttributeGroup group = attributeGroupRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Группа характеристик не найдена: " + uid));

        String author = userService.getCurrentUsername();

        // Логируем в связанные виды характеристик и обнуляем привязку
        List<SprTypeAttributes> relatedAttrs = typeAttributesRepository.findByGroupUid(uid);
        for (SprTypeAttributes attr : relatedAttrs) {
            logTypeAttributeEvent(attr.getUid(), "UPDATE",
                    "'" + attr.getName() + "': Значение поля 'Группа характеристик' изменено с '" + group.getName() + "' на 'null' через справочник 'Группы характеристик'",
                    "Группа характеристик", group.getName(), null, author);
            attr.setGroup(null);
            typeAttributesRepository.save(attr);
        }

        // Логируем в связанные единицы измерения и обнуляем привязку
        List<SprMeasure> relatedMeasures = measureRepository.findByGroupUid(uid);
        for (SprMeasure measure : relatedMeasures) {
            logMeasureEvent(measure.getUid(), "UPDATE",
                    "'" + measure.getName() + "': Значение поля 'Группа характеристик' изменено с '" + group.getName() + "' на 'null' через справочник 'Группы характеристик'",
                    "Группа характеристик", group.getName(), null, author);
            measure.setGroup(null);
            measureRepository.save(measure);
        }

        logEvent(uid, "DELETE", "Удаление группы характеристик: '" + group.getName() + "'",
                null, group.getName(), null, author);

        attributeGroupRepository.delete(group);
    }

    // ==================== EVENTS ====================

    public List<AttributeGroupEventLogDto> getEvents(UUID attributeGroupUid) {
        return eventLogRepository.findByAttributeGroupUidOrderByCreatedAtDesc(attributeGroupUid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<AttributeGroupEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE ====================

    private void logEvent(UUID attributeGroupUid, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        AttributeGroupEventLog log = AttributeGroupEventLog.builder()
                .uid(UUID.randomUUID())
                .attributeGroupUid(attributeGroupUid)
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

    private void logTypeAttributeEvent(UUID typeAttributeUid, String eventType, String description,
                                       String fieldName, String oldValue, String newValue, String author) {
        TypeAttributeEventLog log = TypeAttributeEventLog.builder()
                .uid(UUID.randomUUID())
                .typeAttributeUid(typeAttributeUid)
                .eventType(eventType)
                .eventDescription(description)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .author(author)
                .source("Через справочник 'Группы характеристик'")
                .createdAt(LocalDateTime.now())
                .build();
        typeAttributeEventLogRepository.save(log);
    }

    private void logMeasureEvent(UUID measureUid, String eventType, String description,
                                 String fieldName, String oldValue, String newValue, String author) {
        MeasureEventLog log = MeasureEventLog.builder()
                .uid(UUID.randomUUID())
                .measureUid(measureUid)
                .eventType(eventType)
                .eventDescription(description)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .author(author)
                .source("Через справочник 'Группы характеристик'")
                .createdAt(LocalDateTime.now())
                .build();
        measureEventLogRepository.save(log);
    }

    private AttributeGroupEventLogDto toEventDTO(AttributeGroupEventLog e) {
        return AttributeGroupEventLogDto.builder()
                .uid(e.getUid())
                .attributeGroupUid(e.getAttributeGroupUid())
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

    private Map<String, Object> toRowData(SprAttributeGroup g) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uid", g.getUid().toString());
        row.put("name", g.getName());
        return row;
    }
}
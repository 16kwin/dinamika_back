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
public class MeasureService {

    private final SprMeasureRepository measureRepository;
    private final SprAttributeGroupRepository attributeGroupRepository;
    private final RegAttributesRepository regAttributesRepository;
    private final SprMaterialRepository materialRepository;
    private final MeasureEventLogRepository eventLogRepository;
    private final MeasureColumnSettingsService columnSettingsService;
    private final UserService userService;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of("name", "groupName", "description");
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("name", "groupName", "description"));

    // ==================== GET ALL WITH SETTINGS ====================

    public MeasureListResponse getAllWithSettings(Integer userId) {
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

        List<Map<String, Object>> data = measureRepository.findAll().stream()
                .map(m -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uid", m.getUid().toString());
                    row.put("name", m.getName());
                    row.put("description", m.getDescription());
                    row.put("groupUid", m.getGroup() != null ? m.getGroup().getUid().toString() : null);
                    row.put("groupName", m.getGroup() != null ? m.getGroup().getName() : null);
                    return row;
                })
                .collect(Collectors.toList());

        MeasureListResponse response = new MeasureListResponse();
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
    public Map<String, Object> create(CreateMeasureRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Наименование единицы измерения обязательно");
        }

        SprMeasure measure = new SprMeasure();
        measure.setUid(UUID.randomUUID());
        measure.setName(request.getName());
        measure.setDescription(request.getDescription());
        
        if (request.getGroupUid() != null) {
            SprAttributeGroup group = attributeGroupRepository.findById(request.getGroupUid())
                    .orElseThrow(() -> new RuntimeException("Группа характеристик не найдена: " + request.getGroupUid()));
            measure.setGroup(group);
        }
        
        measure = measureRepository.save(measure);

        String author = userService.getCurrentUsername();
        logEvent(measure.getUid(), "CREATE", "Создание единицы измерения: '" + measure.getName() + "'",
                null, null, null, author);

        return toRowData(measure);
    }

    @Transactional
    public Map<String, Object> update(UUID uid, UpdateMeasureRequest request) {
        SprMeasure measure = measureRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Единица измерения не найдена: " + uid));

        String author = userService.getCurrentUsername();

        if (request.getName() != null && !request.getName().isBlank()) {
            if (!measure.getName().equals(request.getName())) {
                String oldName = measure.getName();
                logEvent(uid, "UPDATE",
                        "'" + oldName + "': Значение поля 'Наименование' изменено с '" + oldName + "' на '" + request.getName() + "'",
                        "Наименование", oldName, request.getName(), author);
                measure.setName(request.getName());
            }
        }

        if (request.getDescription() != null) {
            String oldDescription = measure.getDescription() != null ? measure.getDescription() : "null";
            if (!oldDescription.equals(request.getDescription())) {
                logEvent(uid, "UPDATE",
                        "'" + measure.getName() + "': Значение поля 'Расшифровка' изменено с '" + oldDescription + "' на '" + request.getDescription() + "'",
                        "Расшифровка", oldDescription, request.getDescription(), author);
                measure.setDescription(request.getDescription());
            }
        }

        if (request.getGroupUid() != null) {
            SprAttributeGroup newGroup = attributeGroupRepository.findById(request.getGroupUid())
                    .orElseThrow(() -> new RuntimeException("Группа характеристик не найдена: " + request.getGroupUid()));
            String oldGroupName = measure.getGroup() != null ? measure.getGroup().getName() : "null";
            if (!newGroup.getName().equals(oldGroupName)) {
                logEvent(uid, "UPDATE",
                        "'" + measure.getName() + "': Значение поля 'Группа характеристик' изменено с '" + oldGroupName + "' на '" + newGroup.getName() + "'",
                        "Группа характеристик", oldGroupName, newGroup.getName(), author);
                measure.setGroup(newGroup);
            }
        } else if (request.getGroupUid() == null && measure.getGroup() != null) {
            String oldGroupName = measure.getGroup().getName();
            logEvent(uid, "UPDATE",
                    "'" + measure.getName() + "': Значение поля 'Группа характеристик' изменено с '" + oldGroupName + "' на 'null'",
                    "Группа характеристик", oldGroupName, null, author);
            measure.setGroup(null);
        }

        measure = measureRepository.save(measure);
        return toRowData(measure);
    }

    @Transactional
    public void delete(UUID uid) {
        SprMeasure measure = measureRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Единица измерения не найдена: " + uid));

        String author = userService.getCurrentUsername();

        // Обнуляем привязку в характеристиках материалов
        List<RegAttributes> relatedAttrs = regAttributesRepository.findByMeasureUid(uid);
        for (RegAttributes regAttr : relatedAttrs) {
            regAttr.setMeasure(null);
            regAttributesRepository.save(regAttr);
        }

        // Обнуляем привязку в номенклатуре
        List<SprMaterial> relatedMaterials = materialRepository.findByMeasureUid(uid);
        for (SprMaterial material : relatedMaterials) {
            material.setMeasure(null);
            materialRepository.save(material);
        }

        logEvent(uid, "DELETE", "Удаление единицы измерения: '" + measure.getName() + "'",
                null, measure.getName(), null, author);

        measureRepository.delete(measure);
    }

    // ==================== EVENTS ====================

    public List<MeasureEventLogDto> getEvents(UUID measureUid) {
        return eventLogRepository.findByMeasureUidOrderByCreatedAtDesc(measureUid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<MeasureEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE ====================

    private void logEvent(UUID measureUid, String eventType, String description,
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
                .source("Через карточку")
                .createdAt(LocalDateTime.now())
                .build();
        eventLogRepository.save(log);
    }

    private MeasureEventLogDto toEventDTO(MeasureEventLog e) {
        return MeasureEventLogDto.builder()
                .uid(e.getUid())
                .measureUid(e.getMeasureUid())
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

    private Map<String, Object> toRowData(SprMeasure m) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uid", m.getUid().toString());
        row.put("name", m.getName());
        row.put("description", m.getDescription());
        row.put("groupUid", m.getGroup() != null ? m.getGroup().getUid().toString() : null);
        row.put("groupName", m.getGroup() != null ? m.getGroup().getName() : null);
        return row;
    }
}
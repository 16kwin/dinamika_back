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
public class TypeAttributeService {

    private final SprTypeAttributesRepository typeAttributesRepository;
    private final SprAttributeGroupRepository attributeGroupRepository;
    private final RegAttributesRepository regAttributesRepository;
    private final TypeAttributeEventLogRepository eventLogRepository;
    private final TypeAttributeColumnSettingsService columnSettingsService;
    private final UserService userService;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of("name", "designation", "groupName");
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("name", "designation", "groupName"));

    // ==================== GET ALL WITH SETTINGS ====================

    public TypeAttributeListResponse getAllWithSettings(Integer userId) {
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

        List<Map<String, Object>> data = typeAttributesRepository.findAll().stream()
                .map(a -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uid", a.getUid().toString());
                    row.put("name", a.getName());
                    row.put("designation", a.getDesignation());
                    row.put("groupUid", a.getGroup() != null ? a.getGroup().getUid().toString() : null);
                    row.put("groupName", a.getGroup() != null ? a.getGroup().getName() : null);
                    return row;
                })
                .collect(Collectors.toList());

        TypeAttributeListResponse response = new TypeAttributeListResponse();
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
    public Map<String, Object> create(CreateTypeAttributeRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Наименование вида характеристики обязательно");
        }

        SprTypeAttributes attr = new SprTypeAttributes();
        attr.setUid(UUID.randomUUID());
        attr.setName(request.getName());
        attr.setDesignation(request.getDesignation());
        
        if (request.getGroupUid() != null) {
            SprAttributeGroup group = attributeGroupRepository.findById(request.getGroupUid())
                    .orElseThrow(() -> new RuntimeException("Группа характеристик не найдена: " + request.getGroupUid()));
            attr.setGroup(group);
        }
        
        attr = typeAttributesRepository.save(attr);

        String author = userService.getCurrentUsername();
        logEvent(attr.getUid(), "CREATE", "Создание вида характеристики: '" + attr.getName() + "'",
                null, null, null, author);

        return toRowData(attr);
    }

    @Transactional
    public Map<String, Object> update(UUID uid, UpdateTypeAttributeRequest request) {
        SprTypeAttributes attr = typeAttributesRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Вид характеристики не найден: " + uid));

        String author = userService.getCurrentUsername();

        if (request.getName() != null && !request.getName().isBlank()) {
            if (!attr.getName().equals(request.getName())) {
                String oldName = attr.getName();
                logEvent(uid, "UPDATE",
                        "'" + oldName + "': Значение поля 'Наименование' изменено с '" + oldName + "' на '" + request.getName() + "'",
                        "Наименование", oldName, request.getName(), author);
                attr.setName(request.getName());

                // Логируем в характеристики материалов
                List<RegAttributes> relatedAttrs = regAttributesRepository.findByAttributeTypeUid(uid);
                for (RegAttributes regAttr : relatedAttrs) {
                    if (regAttr.getMaterial() != null) {
                        // Логируем в reg_event_log материала
                        // Это будет сделано через NomenclatureService, пока пропускаем
                    }
                }
            }
        }

        if (request.getDesignation() != null) {
            String oldDesignation = attr.getDesignation() != null ? attr.getDesignation() : "null";
            if (!oldDesignation.equals(request.getDesignation())) {
                logEvent(uid, "UPDATE",
                        "'" + attr.getName() + "': Значение поля 'Обозначение' изменено с '" + oldDesignation + "' на '" + request.getDesignation() + "'",
                        "Обозначение", oldDesignation, request.getDesignation(), author);
                attr.setDesignation(request.getDesignation());
            }
        }

        if (request.getGroupUid() != null) {
            SprAttributeGroup newGroup = attributeGroupRepository.findById(request.getGroupUid())
                    .orElseThrow(() -> new RuntimeException("Группа характеристик не найдена: " + request.getGroupUid()));
            String oldGroupName = attr.getGroup() != null ? attr.getGroup().getName() : "null";
            if (!newGroup.getName().equals(oldGroupName)) {
                logEvent(uid, "UPDATE",
                        "'" + attr.getName() + "': Значение поля 'Группа характеристик' изменено с '" + oldGroupName + "' на '" + newGroup.getName() + "'",
                        "Группа характеристик", oldGroupName, newGroup.getName(), author);
                attr.setGroup(newGroup);
            }
        } else if (request.getGroupUid() == null && attr.getGroup() != null) {
            String oldGroupName = attr.getGroup().getName();
            logEvent(uid, "UPDATE",
                    "'" + attr.getName() + "': Значение поля 'Группа характеристик' изменено с '" + oldGroupName + "' на 'null'",
                    "Группа характеристик", oldGroupName, null, author);
            attr.setGroup(null);
        }

        attr = typeAttributesRepository.save(attr);
        return toRowData(attr);
    }

    @Transactional
    public void delete(UUID uid) {
        SprTypeAttributes attr = typeAttributesRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Вид характеристики не найден: " + uid));

        String author = userService.getCurrentUsername();

        // Обнуляем привязку в характеристиках материалов
        List<RegAttributes> relatedAttrs = regAttributesRepository.findByAttributeTypeUid(uid);
        for (RegAttributes regAttr : relatedAttrs) {
            regAttr.setAttributeType(null);
            regAttributesRepository.save(regAttr);
        }

        logEvent(uid, "DELETE", "Удаление вида характеристики: '" + attr.getName() + "'",
                null, attr.getName(), null, author);

        typeAttributesRepository.delete(attr);
    }

    // ==================== EVENTS ====================

    public List<TypeAttributeEventLogDto> getEvents(UUID typeAttributeUid) {
        return eventLogRepository.findByTypeAttributeUidOrderByCreatedAtDesc(typeAttributeUid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<TypeAttributeEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE ====================

    private void logEvent(UUID typeAttributeUid, String eventType, String description,
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
                .source("Через карточку")
                .createdAt(LocalDateTime.now())
                .build();
        eventLogRepository.save(log);
    }

    private TypeAttributeEventLogDto toEventDTO(TypeAttributeEventLog e) {
        return TypeAttributeEventLogDto.builder()
                .uid(e.getUid())
                .typeAttributeUid(e.getTypeAttributeUid())
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

    private Map<String, Object> toRowData(SprTypeAttributes a) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uid", a.getUid().toString());
        row.put("name", a.getName());
        row.put("designation", a.getDesignation());
        row.put("groupUid", a.getGroup() != null ? a.getGroup().getUid().toString() : null);
        row.put("groupName", a.getGroup() != null ? a.getGroup().getName() : null);
        return row;
    }
}
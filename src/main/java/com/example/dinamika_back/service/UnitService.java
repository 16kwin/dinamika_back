package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.SprUnit;
import com.example.dinamika_back.model.UnitEventLog;
import com.example.dinamika_back.repository.SprUnitRepository;
import com.example.dinamika_back.repository.UnitEventLogRepository;
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
public class UnitService {

    private final SprUnitRepository unitRepository;
    private final UnitEventLogRepository eventLogRepository;
    private final UnitColumnSettingsService columnSettingsService;
    private final UserService userService;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of("name", "description");
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("name", "description"));

    // ==================== GET ALL WITH SETTINGS ====================

    public UnitListResponse getAllWithSettings(Integer userId) {
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

        List<Map<String, Object>> data = unitRepository.findAll().stream()
                .map(u -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uid", u.getUid().toString());
                    row.put("name", u.getName());
                    row.put("description", u.getDescription());
                    return row;
                })
                .collect(Collectors.toList());

        UnitListResponse response = new UnitListResponse();
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
    public Map<String, Object> create(CreateUnitRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Наименование единицы измерения обязательно");
        }

        SprUnit unit = new SprUnit();
        unit.setUid(UUID.randomUUID());
        unit.setName(request.getName());
        unit.setDescription(request.getDescription());
        unit = unitRepository.save(unit);

        String author = userService.getCurrentUsername();
        logEvent(unit.getUid(), "CREATE", "Создание единицы измерения: '" + unit.getName() + "'",
                null, null, null, author);

        return toRowData(unit);
    }

    @Transactional
    public Map<String, Object> update(UUID uid, UpdateUnitRequest request) {
        SprUnit unit = unitRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Единица измерения не найдена: " + uid));

        String author = userService.getCurrentUsername();

        if (request.getName() != null && !request.getName().isBlank()) {
            if (!unit.getName().equals(request.getName())) {
                String oldName = unit.getName();
                logEvent(uid, "UPDATE",
                        "'" + oldName + "': Значение поля 'Наименование' изменено с '" + oldName + "' на '" + request.getName() + "'",
                        "Наименование", oldName, request.getName(), author);
                unit.setName(request.getName());
            }
        }

        if (request.getDescription() != null) {
            String oldDescription = unit.getDescription() != null ? unit.getDescription() : "null";
            if (!oldDescription.equals(request.getDescription())) {
                logEvent(uid, "UPDATE",
                        "'" + unit.getName() + "': Значение поля 'Описание' изменено с '" + oldDescription + "' на '" + request.getDescription() + "'",
                        "Описание", oldDescription, request.getDescription(), author);
                unit.setDescription(request.getDescription());
            }
        }

        unit = unitRepository.save(unit);
        return toRowData(unit);
    }

    @Transactional
    public void delete(UUID uid) {
        SprUnit unit = unitRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Единица измерения не найдена: " + uid));

        String author = userService.getCurrentUsername();

        logEvent(uid, "DELETE", "Удаление единицы измерения: '" + unit.getName() + "'",
                null, unit.getName(), null, author);

        unitRepository.delete(unit);
    }

    // ==================== EVENTS ====================

    public List<UnitEventLogDto> getEvents(UUID unitUid) {
        return eventLogRepository.findByUnitUidOrderByCreatedAtDesc(unitUid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<UnitEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE ====================

    private void logEvent(UUID unitUid, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        UnitEventLog log = UnitEventLog.builder()
                .uid(UUID.randomUUID())
                .unitUid(unitUid)
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

    private UnitEventLogDto toEventDTO(UnitEventLog e) {
        return UnitEventLogDto.builder()
                .uid(e.getUid())
                .unitUid(e.getUnitUid())
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

    private Map<String, Object> toRowData(SprUnit u) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uid", u.getUid().toString());
        row.put("name", u.getName());
        row.put("description", u.getDescription());
        return row;
    }
}
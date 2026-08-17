// StationTypeService.java — ПОЛНЫЙ ФАЙЛ (с getAllWithSettings)
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.StationType;
import com.example.dinamika_back.model.StationTypeEventLog;
import com.example.dinamika_back.repository.StationTypeEventLogRepository;
import com.example.dinamika_back.repository.StationTypeRepository;
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
public class StationTypeService {

    private final StationTypeRepository stationTypeRepository;
    private final StationTypeEventLogRepository eventLogRepository;
    private final StationTypeColumnSettingsService columnSettingsService;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of("name", "description");
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("name"));

    // ==================== GET ALL WITH SETTINGS ====================

    public StationTypeListResponse getAllWithSettings(Integer userId) {
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

        List<StationTypeDto> types = stationTypeRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        StationTypeListResponse response = new StationTypeListResponse();
        response.setColumns(orderedColumns);
        response.setData(types);
        response.setColumnWidths(columnWidths);
        response.setRequiredColumns(new ArrayList<>(requiredColumns));
        return response;
    }

    private void parseColumnSettings(String json, Set<String> visibleColumns, Map<String, Double> columnWidths, Set<String> requiredColumns) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            
            visibleColumns.clear();
            
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                if (value instanceof Boolean) {
                    if ((Boolean) value) {
                        visibleColumns.add(key);
                    }
                } else if (value instanceof Map) {
                    Map<String, Object> settings = (Map<String, Object>) value;
                    Object visible = settings.get("visible");
                    Object width = settings.get("width");
                    Object required = settings.get("required");
                    
                    if (visible instanceof Boolean && (Boolean) visible) {
                        visibleColumns.add(key);
                    }
                    
                    if (width instanceof Number) {
                        columnWidths.put(key, ((Number) width).doubleValue());
                    }
                    
                    if (required instanceof Boolean && (Boolean) required) {
                        requiredColumns.add(key);
                    }
                }
            }
        } catch (Exception e) {
            visibleColumns.clear();
            visibleColumns.addAll(ALL_COLUMNS_ORDER);
        }
    }

    // ==================== CRUD ====================

    public List<StationTypeDto> getAll() {
        return stationTypeRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public StationTypeDto getById(UUID uid) {
        StationType type = stationTypeRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Тип станции не найден: " + uid));
        return toDTO(type);
    }

    @Transactional
    public StationTypeDto create(CreateStationTypeRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Наименование типа обязательно");
        }
        if (stationTypeRepository.existsByName(request.getName())) {
            throw new RuntimeException("Тип станции с таким именем уже существует: " + request.getName());
        }
        StationType type = new StationType();
        type.setUid(UUID.randomUUID());
        type.setName(request.getName());
        type.setDescription(request.getDescription());
        type = stationTypeRepository.save(type);

        logEvent(type.getUid(), "CREATE", "Создание типа станции", null, null, null, "Система");

        return toDTO(type);
    }

    @Transactional
    public StationTypeDto update(UUID uid, UpdateStationTypeRequest request) {
        StationType type = stationTypeRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Тип станции не найден: " + uid));

        if (request.getName() != null && !request.getName().isBlank()
                && !type.getName().equals(request.getName())
                && stationTypeRepository.existsByName(request.getName())) {
            throw new RuntimeException("Тип станции с таким именем уже существует: " + request.getName());
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            if (!type.getName().equals(request.getName())) {
                logFieldChange(uid, "Наименование", type.getName(), request.getName(), "Система");
                type.setName(request.getName());
            }
        }
        if (request.getDescription() != null) {
            if (!request.getDescription().equals(type.getDescription())) {
                logFieldChange(uid, "Описание", type.getDescription(), request.getDescription(), "Система");
                type.setDescription(request.getDescription());
            }
        }

        type = stationTypeRepository.save(type);
        return toDTO(type);
    }

    @Transactional
    public void delete(UUID uid) {
        StationType type = stationTypeRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Тип станции не найден: " + uid));

        logEvent(uid, "DELETE", "Удаление типа станции", null, type.getName(), null, "Система");
        stationTypeRepository.delete(type);
    }

    // ==================== EVENTS ====================

    public List<StationTypeEventLogDto> getEvents(UUID stationTypeUid) {
        return eventLogRepository.findByStationTypeUidOrderByCreatedAtDesc(stationTypeUid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<StationTypeEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE ====================

    private void logEvent(UUID stationTypeUid, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        StationTypeEventLog log = StationTypeEventLog.builder()
                .uid(UUID.randomUUID())
                .stationTypeUid(stationTypeUid)
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

    private void logFieldChange(UUID stationTypeUid, String fieldName, String oldValue, String newValue, String author) {
        if (oldValue == null && newValue == null) return;
        if (oldValue != null && oldValue.equals(newValue)) return;

        if (oldValue == null && newValue != null) {
            logEvent(stationTypeUid, "UPDATE", "Значение поля '" + fieldName + "' установлено: " + newValue,
                    fieldName, null, newValue, author);
        } else if (newValue == null && oldValue != null) {
            logEvent(stationTypeUid, "UPDATE", "Значение поля '" + fieldName + "' очищено",
                    fieldName, oldValue, null, author);
        } else {
            logEvent(stationTypeUid, "UPDATE", "Значение поля '" + fieldName + "' изменено с '" + oldValue + "' на '" + newValue + "'",
                    fieldName, oldValue, newValue, author);
        }
    }

    private StationTypeEventLogDto toEventDTO(StationTypeEventLog e) {
        return StationTypeEventLogDto.builder()
                .uid(e.getUid())
                .stationTypeUid(e.getStationTypeUid())
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

    private StationTypeDto toDTO(StationType type) {
        return new StationTypeDto(type.getUid(), type.getName(), type.getDescription());
    }
}
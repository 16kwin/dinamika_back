// StationManufacturerService.java — ПОЛНЫЙ ФАЙЛ (с getAllWithSettings)
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.SprCountry;
import com.example.dinamika_back.model.StationManufacturer;
import com.example.dinamika_back.model.StationManufacturerEventLog;
import com.example.dinamika_back.repository.SprCountryRepository;
import com.example.dinamika_back.repository.StationManufacturerEventLogRepository;
import com.example.dinamika_back.repository.StationManufacturerRepository;
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
public class StationManufacturerService {

    private final StationManufacturerRepository manufacturerRepository;
    private final SprCountryRepository countryRepository;
    private final StationManufacturerEventLogRepository eventLogRepository;
    private final StationManufacturerColumnSettingsService columnSettingsService;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of("name", "description", "countryName");
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("name", "countryName"));

    // ==================== GET ALL WITH SETTINGS ====================

    public StationManufacturerListResponse getAllWithSettings(Integer userId) {
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

        List<StationManufacturerDto> manufacturers = manufacturerRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        StationManufacturerListResponse response = new StationManufacturerListResponse();
        response.setColumns(orderedColumns);
        response.setData(manufacturers);
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

    public List<StationManufacturerDto> getAll() {
        return manufacturerRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public StationManufacturerDto getById(UUID uid) {
        StationManufacturer manufacturer = manufacturerRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Производитель не найден: " + uid));
        return toDTO(manufacturer);
    }

    @Transactional
    public StationManufacturerDto create(CreateStationManufacturerRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Наименование производителя обязательно");
        }
        if (manufacturerRepository.existsByName(request.getName())) {
            throw new RuntimeException("Производитель с таким именем уже существует: " + request.getName());
        }
        StationManufacturer manufacturer = new StationManufacturer();
        manufacturer.setUid(UUID.randomUUID());
        manufacturer.setName(request.getName());
        manufacturer.setDescription(request.getDescription());

        if (request.getCountryUid() != null) {
            SprCountry country = countryRepository.findById(request.getCountryUid())
                    .orElseThrow(() -> new RuntimeException("Страна не найдена: " + request.getCountryUid()));
            manufacturer.setCountry(country);
        }

        manufacturer = manufacturerRepository.save(manufacturer);

        logEvent(manufacturer.getUid(), "CREATE", "Создание производителя", null, null, null, "Система");

        return toDTO(manufacturer);
    }

    @Transactional
    public StationManufacturerDto update(UUID uid, UpdateStationManufacturerRequest request) {
        StationManufacturer manufacturer = manufacturerRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Производитель не найден: " + uid));

        if (request.getName() != null && !request.getName().isBlank()
                && !manufacturer.getName().equals(request.getName())
                && manufacturerRepository.existsByName(request.getName())) {
            throw new RuntimeException("Производитель с таким именем уже существует: " + request.getName());
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            if (!manufacturer.getName().equals(request.getName())) {
                logFieldChange(uid, "Наименование", manufacturer.getName(), request.getName(), "Система");
                manufacturer.setName(request.getName());
            }
        }
        if (request.getDescription() != null) {
            if (!request.getDescription().equals(manufacturer.getDescription())) {
                logFieldChange(uid, "Описание", manufacturer.getDescription(), request.getDescription(), "Система");
                manufacturer.setDescription(request.getDescription());
            }
        }

        if (request.getCountryUid() != null) {
            String oldCountryName = manufacturer.getCountry() != null ? manufacturer.getCountry().getName() : null;
            SprCountry country = countryRepository.findById(request.getCountryUid())
                    .orElseThrow(() -> new RuntimeException("Страна не найдена: " + request.getCountryUid()));
            if (oldCountryName == null || !oldCountryName.equals(country.getName())) {
                logFieldChange(uid, "Страна", oldCountryName, country.getName(), "Система");
                manufacturer.setCountry(country);
            }
        } else {
            if (manufacturer.getCountry() != null) {
                logFieldChange(uid, "Страна", manufacturer.getCountry().getName(), null, "Система");
                manufacturer.setCountry(null);
            }
        }

        manufacturer = manufacturerRepository.save(manufacturer);
        return toDTO(manufacturer);
    }

    @Transactional
    public void delete(UUID uid) {
        StationManufacturer manufacturer = manufacturerRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Производитель не найден: " + uid));

        logEvent(uid, "DELETE", "Удаление производителя", null, manufacturer.getName(), null, "Система");
        manufacturerRepository.delete(manufacturer);
    }

    // ==================== EVENTS ====================

    public List<StationManufacturerEventLogDto> getEvents(UUID stationManufacturerUid) {
        return eventLogRepository.findByStationManufacturerUidOrderByCreatedAtDesc(stationManufacturerUid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<StationManufacturerEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE ====================

    private void logEvent(UUID manufacturerUid, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        StationManufacturerEventLog log = StationManufacturerEventLog.builder()
                .uid(UUID.randomUUID())
                .stationManufacturerUid(manufacturerUid)
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

    private void logFieldChange(UUID manufacturerUid, String fieldName, String oldValue, String newValue, String author) {
        if (oldValue == null && newValue == null) return;
        if (oldValue != null && oldValue.equals(newValue)) return;

        if (oldValue == null && newValue != null) {
            logEvent(manufacturerUid, "UPDATE", "Значение поля '" + fieldName + "' установлено: " + newValue,
                    fieldName, null, newValue, author);
        } else if (newValue == null && oldValue != null) {
            logEvent(manufacturerUid, "UPDATE", "Значение поля '" + fieldName + "' очищено",
                    fieldName, oldValue, null, author);
        } else {
            logEvent(manufacturerUid, "UPDATE", "Значение поля '" + fieldName + "' изменено с '" + oldValue + "' на '" + newValue + "'",
                    fieldName, oldValue, newValue, author);
        }
    }

    private StationManufacturerEventLogDto toEventDTO(StationManufacturerEventLog e) {
        return StationManufacturerEventLogDto.builder()
                .uid(e.getUid())
                .stationManufacturerUid(e.getStationManufacturerUid())
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

    private StationManufacturerDto toDTO(StationManufacturer manufacturer) {
        return StationManufacturerDto.builder()
                .uid(manufacturer.getUid())
                .name(manufacturer.getName())
                .description(manufacturer.getDescription())
                .countryUid(manufacturer.getCountry() != null ? manufacturer.getCountry().getUid() : null)
                .countryName(manufacturer.getCountry() != null ? manufacturer.getCountry().getName() : null)
                .build();
    }
}
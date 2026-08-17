// HoldingService.java — ПОЛНЫЙ ФАЙЛ (без uid)
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.Holding;
import com.example.dinamika_back.model.HoldingEventLog;
import com.example.dinamika_back.model.Location;
import com.example.dinamika_back.repository.HoldingEventLogRepository;
import com.example.dinamika_back.repository.HoldingRepository;
import com.example.dinamika_back.repository.LocationRepository;
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
public class HoldingService {

    private final HoldingRepository holdingRepository;
    private final LocationRepository locationRepository;
    private final HoldingEventLogRepository eventLogRepository;
    private final HoldingColumnSettingsService columnSettingsService;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of("name", "description", "locationName");
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("name"));

    // ==================== GET ALL WITH SETTINGS ====================

    public HoldingListResponse getAllWithSettings(Integer userId) {
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

        List<HoldingFlatDto> holdings = holdingRepository.findAllByOrderByNameAsc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        HoldingListResponse response = new HoldingListResponse();
        response.setColumns(orderedColumns);
        response.setData(holdings);
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

    public List<HoldingFlatDto> getAll() {
        return holdingRepository.findAllByOrderByNameAsc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public HoldingFlatDto getById(Long id) {
        Holding holding = holdingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Холдинг не найден: " + id));
        return toDTO(holding);
    }

    @Transactional
    public HoldingFlatDto create(CreateHoldingRequest request) {
        if (holdingRepository.existsByName(request.getName())) {
            throw new RuntimeException("Холдинг с таким именем уже существует: " + request.getName());
        }
        Holding holding = new Holding();
        holding.setName(request.getName());
        holding.setDescription(request.getDescription());

        if (request.getLocationUid() != null) {
            Location location = locationRepository.findById(request.getLocationUid())
                    .orElseThrow(() -> new RuntimeException("Расположение не найдено: " + request.getLocationUid()));
            holding.setLocation(location);
        }

        holding = holdingRepository.save(holding);

        logEvent(holding.getId(), "CREATE", "Создание холдинга", null, null, null, "Система");

        return toDTO(holding);
    }

    @Transactional
    public HoldingFlatDto update(Long id, UpdateHoldingRequest request) {
        Holding holding = holdingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Холдинг не найден: " + id));

        if (!holding.getName().equals(request.getName())
                && holdingRepository.existsByName(request.getName())) {
            throw new RuntimeException("Холдинг с таким именем уже существует: " + request.getName());
        }

        if (!holding.getName().equals(request.getName())) {
            logFieldChange(id, "Наименование", holding.getName(), request.getName(), "Система");
            holding.setName(request.getName());
        }

        if (request.getDescription() != null && !request.getDescription().equals(holding.getDescription())) {
            logFieldChange(id, "Описание", holding.getDescription(), request.getDescription(), "Система");
            holding.setDescription(request.getDescription());
        }

        if (request.getLocationUid() != null) {
            String oldLocationName = holding.getLocation() != null ? holding.getLocation().getName() : null;
            Location location = locationRepository.findById(request.getLocationUid())
                    .orElseThrow(() -> new RuntimeException("Расположение не найдено: " + request.getLocationUid()));
            if (oldLocationName == null || !oldLocationName.equals(location.getName())) {
                logFieldChange(id, "Расположение", oldLocationName, location.getName(), "Система");
                holding.setLocation(location);
            }
        } else {
            if (holding.getLocation() != null) {
                logFieldChange(id, "Расположение", holding.getLocation().getName(), null, "Система");
                holding.setLocation(null);
            }
        }

        holding = holdingRepository.save(holding);
        return toDTO(holding);
    }

    @Transactional
    public void delete(Long id) {
        Holding holding = holdingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Холдинг не найден: " + id));

        logEvent(id, "DELETE", "Удаление холдинга", null, holding.getName(), null, "Система");
        holdingRepository.delete(holding);
    }

    // ==================== EVENTS ====================

    public List<HoldingEventLogDto> getEvents(Long holdingId) {
        return eventLogRepository.findByHoldingIdOrderByCreatedAtDesc(holdingId).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<HoldingEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE ====================

    private void logEvent(Long holdingId, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        HoldingEventLog log = HoldingEventLog.builder()
                .uid(UUID.randomUUID())
                .holdingId(holdingId)
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

    private void logFieldChange(Long holdingId, String fieldName, String oldValue, String newValue, String author) {
        if (oldValue == null && newValue == null) return;
        if (oldValue != null && oldValue.equals(newValue)) return;

        if (oldValue == null && newValue != null) {
            logEvent(holdingId, "UPDATE", "Значение поля '" + fieldName + "' установлено: " + newValue,
                    fieldName, null, newValue, author);
        } else if (newValue == null && oldValue != null) {
            logEvent(holdingId, "UPDATE", "Значение поля '" + fieldName + "' очищено",
                    fieldName, oldValue, null, author);
        } else {
            logEvent(holdingId, "UPDATE", "Значение поля '" + fieldName + "' изменено с '" + oldValue + "' на '" + newValue + "'",
                    fieldName, oldValue, newValue, author);
        }
    }

    private HoldingEventLogDto toEventDTO(HoldingEventLog e) {
        return HoldingEventLogDto.builder()
                .uid(e.getUid())
                .holdingId(e.getHoldingId())
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

    private HoldingFlatDto toDTO(Holding holding) {
        return HoldingFlatDto.builder()
                .id(holding.getId())
                .name(holding.getName())
                .description(holding.getDescription())
                .locationUid(holding.getLocation() != null ? holding.getLocation().getUid() : null)
                .locationName(holding.getLocation() != null ? holding.getLocation().getName() : null)
                .build();
    }
}
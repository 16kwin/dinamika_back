// EnterpriseService.java — ПОЛНЫЙ ФАЙЛ (с address и getAllWithSettings)
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.Enterprise;
import com.example.dinamika_back.model.EnterpriseEventLog;
import com.example.dinamika_back.model.Holding;
import com.example.dinamika_back.model.Location;
import com.example.dinamika_back.repository.EnterpriseEventLogRepository;
import com.example.dinamika_back.repository.EnterpriseRepository;
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
public class EnterpriseService {

    private final EnterpriseRepository enterpriseRepository;
    private final HoldingRepository holdingRepository;
    private final LocationRepository locationRepository;
    private final EnterpriseEventLogRepository eventLogRepository;
    private final EnterpriseColumnSettingsService columnSettingsService;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of("name", "description", "address", "holdingName", "locationName");
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("name"));

    // ==================== GET ALL WITH SETTINGS ====================

    public EnterpriseListResponse getAllWithSettings(Integer userId) {
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

        List<EnterpriseFlatDto> enterprises = enterpriseRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        EnterpriseListResponse response = new EnterpriseListResponse();
        response.setColumns(orderedColumns);
        response.setData(enterprises);
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

    public List<EnterpriseFlatDto> getAll() {
        return enterpriseRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<EnterpriseFlatDto> getByHoldingId(Long holdingId) {
        return enterpriseRepository.findByHoldingIdOrderByNameAsc(holdingId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public EnterpriseFlatDto getById(Long id) {
        Enterprise enterprise = enterpriseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Предприятие не найдено: " + id));
        return toDTO(enterprise);
    }

    @Transactional
    public EnterpriseFlatDto create(CreateEnterpriseRequest request) {
        if (enterpriseRepository.existsByName(request.getName())) {
            throw new RuntimeException("Предприятие с таким именем уже существует: " + request.getName());
        }
        Enterprise enterprise = new Enterprise();
        enterprise.setName(request.getName());
        enterprise.setDescription(request.getDescription());
        enterprise.setAddress(request.getAddress());

        if (request.getHoldingId() != null) {
            Holding holding = holdingRepository.findById(request.getHoldingId())
                    .orElseThrow(() -> new RuntimeException("Холдинг не найден: " + request.getHoldingId()));
            enterprise.setHolding(holding);
        }

        if (request.getLocationUid() != null) {
            Location location = locationRepository.findById(request.getLocationUid())
                    .orElseThrow(() -> new RuntimeException("Расположение не найдено: " + request.getLocationUid()));
            enterprise.setLocation(location);
        }

        enterprise = enterpriseRepository.save(enterprise);

        logEvent(enterprise.getId(), "CREATE", "Создание предприятия", null, null, null, "Система");

        return toDTO(enterprise);
    }

    @Transactional
    public EnterpriseFlatDto update(Long id, UpdateEnterpriseRequest request) {
        Enterprise enterprise = enterpriseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Предприятие не найдено: " + id));

        if (!enterprise.getName().equals(request.getName())
                && enterpriseRepository.existsByName(request.getName())) {
            throw new RuntimeException("Предприятие с таким именем уже существует: " + request.getName());
        }

        if (!enterprise.getName().equals(request.getName())) {
            logFieldChange(id, "Наименование", enterprise.getName(), request.getName(), "Система");
            enterprise.setName(request.getName());
        }

        if (request.getDescription() != null && !Objects.equals(request.getDescription(), enterprise.getDescription())) {
            logFieldChange(id, "Описание", enterprise.getDescription(), request.getDescription(), "Система");
            enterprise.setDescription(request.getDescription());
        }

        if (request.getAddress() != null && !Objects.equals(request.getAddress(), enterprise.getAddress())) {
            logFieldChange(id, "Адрес", enterprise.getAddress(), request.getAddress(), "Система");
            enterprise.setAddress(request.getAddress());
        }

        if (request.getHoldingId() != null) {
            String oldHoldingName = enterprise.getHolding() != null ? enterprise.getHolding().getName() : null;
            Holding holding = holdingRepository.findById(request.getHoldingId())
                    .orElseThrow(() -> new RuntimeException("Холдинг не найден: " + request.getHoldingId()));
            if (oldHoldingName == null || !oldHoldingName.equals(holding.getName())) {
                logFieldChange(id, "Холдинг", oldHoldingName, holding.getName(), "Система");
                enterprise.setHolding(holding);
            }
        } else {
            if (enterprise.getHolding() != null) {
                logFieldChange(id, "Холдинг", enterprise.getHolding().getName(), null, "Система");
                enterprise.setHolding(null);
            }
        }

        if (request.getLocationUid() != null) {
            String oldLocationName = enterprise.getLocation() != null ? enterprise.getLocation().getName() : null;
            Location location = locationRepository.findById(request.getLocationUid())
                    .orElseThrow(() -> new RuntimeException("Расположение не найдено: " + request.getLocationUid()));
            if (oldLocationName == null || !oldLocationName.equals(location.getName())) {
                logFieldChange(id, "Расположение", oldLocationName, location.getName(), "Система");
                enterprise.setLocation(location);
            }
        } else {
            if (enterprise.getLocation() != null) {
                logFieldChange(id, "Расположение", enterprise.getLocation().getName(), null, "Система");
                enterprise.setLocation(null);
            }
        }

        enterprise = enterpriseRepository.save(enterprise);
        return toDTO(enterprise);
    }

    @Transactional
    public void delete(Long id) {
        Enterprise enterprise = enterpriseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Предприятие не найдено: " + id));

        logEvent(id, "DELETE", "Удаление предприятия", null, enterprise.getName(), null, "Система");
        enterpriseRepository.delete(enterprise);
    }

    // ==================== EVENTS ====================

    public List<EnterpriseEventLogDto> getEvents(Long enterpriseId) {
        return eventLogRepository.findByEnterpriseIdOrderByCreatedAtDesc(enterpriseId).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<EnterpriseEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE ====================

    private void logEvent(Long enterpriseId, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        EnterpriseEventLog log = EnterpriseEventLog.builder()
                .uid(UUID.randomUUID())
                .enterpriseId(enterpriseId)
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

    private void logFieldChange(Long enterpriseId, String fieldName, String oldValue, String newValue, String author) {
        if (oldValue == null && newValue == null) return;
        if (oldValue != null && oldValue.equals(newValue)) return;

        if (oldValue == null && newValue != null) {
            logEvent(enterpriseId, "UPDATE", "Значение поля '" + fieldName + "' установлено: " + newValue,
                    fieldName, null, newValue, author);
        } else if (newValue == null && oldValue != null) {
            logEvent(enterpriseId, "UPDATE", "Значение поля '" + fieldName + "' очищено",
                    fieldName, oldValue, null, author);
        } else {
            logEvent(enterpriseId, "UPDATE", "Значение поля '" + fieldName + "' изменено с '" + oldValue + "' на '" + newValue + "'",
                    fieldName, oldValue, newValue, author);
        }
    }

    private EnterpriseEventLogDto toEventDTO(EnterpriseEventLog e) {
        return EnterpriseEventLogDto.builder()
                .uid(e.getUid())
                .enterpriseId(e.getEnterpriseId())
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

    private EnterpriseFlatDto toDTO(Enterprise enterprise) {
        return EnterpriseFlatDto.builder()
                .id(enterprise.getId())
                .name(enterprise.getName())
                .description(enterprise.getDescription())
                .address(enterprise.getAddress())
                .holdingId(enterprise.getHolding() != null ? enterprise.getHolding().getId() : null)
                .holdingName(enterprise.getHolding() != null ? enterprise.getHolding().getName() : null)
                .locationUid(enterprise.getLocation() != null ? enterprise.getLocation().getUid() : null)
                .locationName(enterprise.getLocation() != null ? enterprise.getLocation().getName() : null)
                .build();
    }
}
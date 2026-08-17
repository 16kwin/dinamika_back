// WorkshopService.java — ПОЛНЫЙ ФАЙЛ (с getAllWithSettings и address)
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.Enterprise;
import com.example.dinamika_back.model.Location;
import com.example.dinamika_back.model.Workshop;
import com.example.dinamika_back.model.WorkshopEventLog;
import com.example.dinamika_back.repository.EnterpriseRepository;
import com.example.dinamika_back.repository.LocationRepository;
import com.example.dinamika_back.repository.WorkshopEventLogRepository;
import com.example.dinamika_back.repository.WorkshopRepository;
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
public class WorkshopService {

    private final WorkshopRepository workshopRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final LocationRepository locationRepository;
    private final WorkshopEventLogRepository eventLogRepository;
    private final WorkshopColumnSettingsService columnSettingsService;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of("name", "description", "address", "enterpriseName", "holdingName", "locationName");
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("name"));

    // ==================== GET ALL WITH SETTINGS ====================

    public WorkshopListResponse getAllWithSettings(Integer userId) {
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

        List<WorkshopFlatDto> workshops = workshopRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        WorkshopListResponse response = new WorkshopListResponse();
        response.setColumns(orderedColumns);
        response.setData(workshops);
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

    public List<WorkshopFlatDto> getAll() {
        return workshopRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<WorkshopFlatDto> getByEnterpriseId(Long enterpriseId) {
        return workshopRepository.findByEnterpriseId(enterpriseId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public WorkshopFlatDto getById(Long id) {
        Workshop workshop = workshopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Цех не найден: " + id));
        return toDTO(workshop);
    }

    @Transactional
    public WorkshopFlatDto create(CreateWorkshopRequest request) {
        Enterprise enterprise = enterpriseRepository.findById(request.getEnterpriseId())
                .orElseThrow(() -> new RuntimeException("Предприятие не найдено: " + request.getEnterpriseId()));
        if (workshopRepository.existsByNameAndEnterpriseId(request.getName(), request.getEnterpriseId())) {
            throw new RuntimeException("Цех с таким именем уже существует на этом предприятии");
        }
        Workshop workshop = new Workshop();
        workshop.setName(request.getName());
        workshop.setDescription(request.getDescription());
        workshop.setAddress(request.getAddress());
        workshop.setEnterprise(enterprise);

        if (request.getLocationUid() != null) {
            Location location = locationRepository.findById(request.getLocationUid())
                    .orElseThrow(() -> new RuntimeException("Расположение не найдено: " + request.getLocationUid()));
            workshop.setLocation(location);
        }

        workshop = workshopRepository.save(workshop);

        logEvent(workshop.getId(), "CREATE", "Создание цеха", null, null, null, "Система");

        return toDTO(workshop);
    }

    @Transactional
    public WorkshopFlatDto update(Long id, UpdateWorkshopRequest request) {
        Workshop workshop = workshopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Цех не найден: " + id));
        Enterprise enterprise = enterpriseRepository.findById(request.getEnterpriseId())
                .orElseThrow(() -> new RuntimeException("Предприятие не найдено: " + request.getEnterpriseId()));

        if (!workshop.getName().equals(request.getName())
                || !workshop.getEnterprise().getId().equals(request.getEnterpriseId())) {
            if (workshopRepository.existsByNameAndEnterpriseId(request.getName(), request.getEnterpriseId())) {
                throw new RuntimeException("Цех с таким именем уже существует на этом предприятии");
            }
        }

        if (!workshop.getName().equals(request.getName())) {
            logFieldChange(id, "Наименование", workshop.getName(), request.getName(), "Система");
            workshop.setName(request.getName());
        }

        if (request.getDescription() != null && !Objects.equals(request.getDescription(), workshop.getDescription())) {
            logFieldChange(id, "Описание", workshop.getDescription(), request.getDescription(), "Система");
            workshop.setDescription(request.getDescription());
        }

        if (request.getAddress() != null && !Objects.equals(request.getAddress(), workshop.getAddress())) {
            logFieldChange(id, "Адрес", workshop.getAddress(), request.getAddress(), "Система");
            workshop.setAddress(request.getAddress());
        }

        if (!workshop.getEnterprise().getId().equals(request.getEnterpriseId())) {
            logFieldChange(id, "Предприятие", workshop.getEnterprise().getName(), enterprise.getName(), "Система");
            workshop.setEnterprise(enterprise);
        }

        if (request.getLocationUid() != null) {
            String oldLocationName = workshop.getLocation() != null ? workshop.getLocation().getName() : null;
            Location location = locationRepository.findById(request.getLocationUid())
                    .orElseThrow(() -> new RuntimeException("Расположение не найдено: " + request.getLocationUid()));
            if (oldLocationName == null || !oldLocationName.equals(location.getName())) {
                logFieldChange(id, "Расположение", oldLocationName, location.getName(), "Система");
                workshop.setLocation(location);
            }
        } else {
            if (workshop.getLocation() != null) {
                logFieldChange(id, "Расположение", workshop.getLocation().getName(), null, "Система");
                workshop.setLocation(null);
            }
        }

        workshop = workshopRepository.save(workshop);
        return toDTO(workshop);
    }

    @Transactional
    public void delete(Long id) {
        Workshop workshop = workshopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Цех не найден: " + id));

        logEvent(id, "DELETE", "Удаление цеха", null, workshop.getName(), null, "Система");
        workshopRepository.delete(workshop);
    }

    // ==================== EVENTS ====================

    public List<WorkshopEventLogDto> getEvents(Long workshopId) {
        return eventLogRepository.findByWorkshopIdOrderByCreatedAtDesc(workshopId).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<WorkshopEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE ====================

    private void logEvent(Long workshopId, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        WorkshopEventLog log = WorkshopEventLog.builder()
                .uid(UUID.randomUUID())
                .workshopId(workshopId)
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

    private void logFieldChange(Long workshopId, String fieldName, String oldValue, String newValue, String author) {
        if (oldValue == null && newValue == null) return;
        if (oldValue != null && oldValue.equals(newValue)) return;

        if (oldValue == null && newValue != null) {
            logEvent(workshopId, "UPDATE", "Значение поля '" + fieldName + "' установлено: " + newValue,
                    fieldName, null, newValue, author);
        } else if (newValue == null && oldValue != null) {
            logEvent(workshopId, "UPDATE", "Значение поля '" + fieldName + "' очищено",
                    fieldName, oldValue, null, author);
        } else {
            logEvent(workshopId, "UPDATE", "Значение поля '" + fieldName + "' изменено с '" + oldValue + "' на '" + newValue + "'",
                    fieldName, oldValue, newValue, author);
        }
    }

    private WorkshopEventLogDto toEventDTO(WorkshopEventLog e) {
        return WorkshopEventLogDto.builder()
                .uid(e.getUid())
                .workshopId(e.getWorkshopId())
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

    private WorkshopFlatDto toDTO(Workshop workshop) {
        return WorkshopFlatDto.builder()
                .id(workshop.getId())
                .name(workshop.getName())
                .description(workshop.getDescription())
                .address(workshop.getAddress())
                .holdingId(workshop.getEnterprise() != null && workshop.getEnterprise().getHolding() != null
                        ? workshop.getEnterprise().getHolding().getId() : null)
                .holdingName(workshop.getEnterprise() != null && workshop.getEnterprise().getHolding() != null
                        ? workshop.getEnterprise().getHolding().getName() : null)
                .enterpriseId(workshop.getEnterprise() != null ? workshop.getEnterprise().getId() : null)
                .enterpriseName(workshop.getEnterprise() != null ? workshop.getEnterprise().getName() : null)
                .locationUid(workshop.getLocation() != null ? workshop.getLocation().getUid() : null)
                .locationName(workshop.getLocation() != null ? workshop.getLocation().getName() : null)
                .build();
    }
}
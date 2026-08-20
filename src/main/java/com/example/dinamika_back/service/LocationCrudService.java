// LocationCrudService.java — ПОЛНЫЙ ФАЙЛ (добавлено логирование в цеха)
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.LocationEventLogDto;
import com.example.dinamika_back.dto.LocationFlatDto;
import com.example.dinamika_back.dto.LocationListResponse;
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
public class LocationCrudService {

    private final LocationRepository locationRepository;
    private final LocationEventLogRepository eventLogRepository;
    private final UserLocationColumnSettingsRepository columnSettingsRepository;
    private final UserService userService;
    private final HoldingRepository holdingRepository;
    private final HoldingEventLogRepository holdingEventLogRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final EnterpriseEventLogRepository enterpriseEventLogRepository;
    private final WorkshopRepository workshopRepository;
    private final WorkshopEventLogRepository workshopEventLogRepository;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of("name");
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("name"));

    // ==================== GET ALL WITH SETTINGS ====================

    public LocationListResponse getAllWithSettings(Integer userId) {
        String columnsJson = columnSettingsRepository.findByUserId(userId)
                .map(UserLocationColumnSettings::getColumnsJson)
                .orElse(null);
        Set<String> visibleColumns = new LinkedHashSet<>(ALL_COLUMNS_ORDER);
        Map<String, Double> columnWidths = new HashMap<>();
        Set<String> requiredColumns = new LinkedHashSet<>(REQUIRED_COLUMNS);

        if (columnsJson != null && !columnsJson.isEmpty()) {
            parseColumnSettings(columnsJson, visibleColumns, columnWidths, requiredColumns);
        }

        List<String> orderedColumns = ALL_COLUMNS_ORDER.stream()
                .filter(visibleColumns::contains)
                .collect(Collectors.toList());

        List<LocationFlatDto> locations = locationRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new LocationListResponse(orderedColumns, locations, columnWidths, new ArrayList<>(requiredColumns));
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

    public List<LocationFlatDto> getAll() {
        return locationRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public LocationFlatDto getById(UUID uid) {
        Location location = locationRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Расположение не найдено: " + uid));
        return toDTO(location);
    }

    @Transactional
    public LocationFlatDto create(String name) {
        if (name == null || name.isBlank()) {
            throw new RuntimeException("Наименование расположения обязательно");
        }
        if (locationRepository.existsByName(name)) {
            throw new RuntimeException("Расположение с таким именем уже существует: " + name);
        }

        Location location = new Location();
        location.setUid(UUID.randomUUID());
        location.setName(name);
        location = locationRepository.save(location);

        logEvent(location.getUid(), "CREATE", "Создание расположения: '" + name + "'", null, null, null, userService.getCurrentUsername());

        return toDTO(location);
    }

    @Transactional
    public LocationFlatDto update(UUID uid, String name) {
        Location location = locationRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Расположение не найдено: " + uid));

        String author = userService.getCurrentUsername();

        if (name != null && !name.isBlank() && !location.getName().equals(name)) {
            if (locationRepository.existsByName(name)) {
                throw new RuntimeException("Расположение с таким именем уже существует: " + name);
            }
            String oldName = location.getName();
            logEvent(uid, "UPDATE", "'" + oldName + "': Значение поля 'Наименование' изменено с '" + oldName + "' на '" + name + "'",
                    "Наименование", oldName, name, author);
            location.setName(name);

            // Логируем в историю холдингов
            List<Holding> relatedHoldings = holdingRepository.findByLocationUid(uid);
            for (Holding holding : relatedHoldings) {
                logHoldingEvent(holding.getId(), "UPDATE",
                        "'" + holding.getName() + "': Значение поля 'Расположение' изменено с '" + oldName + "' на '" + name + "' через справочник 'Расположения'",
                        "Расположение", oldName, name, author);
            }

            // Логируем в историю предприятий
            List<Enterprise> relatedEnterprises = enterpriseRepository.findByLocationUid(uid);
            for (Enterprise enterprise : relatedEnterprises) {
                logEnterpriseEvent(enterprise.getId(), "UPDATE",
                        "'" + enterprise.getName() + "': Значение поля 'Расположение' изменено с '" + oldName + "' на '" + name + "' через справочник 'Расположения'",
                        "Расположение", oldName, name, author);
            }

            // Логируем в историю цехов
            List<Workshop> relatedWorkshops = workshopRepository.findAll().stream()
                    .filter(w -> w.getLocation() != null && w.getLocation().getUid().equals(uid))
                    .collect(Collectors.toList());
            for (Workshop workshop : relatedWorkshops) {
                logWorkshopEvent(workshop.getId(), "UPDATE",
                        "'" + workshop.getName() + "': Значение поля 'Расположение' изменено с '" + oldName + "' на '" + name + "' через справочник 'Расположения'",
                        "Расположение", oldName, name, author);
            }
        }

        location = locationRepository.save(location);
        return toDTO(location);
    }

    @Transactional
    public void delete(UUID uid) {
        Location location = locationRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Расположение не найдено: " + uid));

        String author = userService.getCurrentUsername();
        String locationName = location.getName();

        // Логируем в историю холдингов
        List<Holding> relatedHoldings = holdingRepository.findByLocationUid(uid);
        for (Holding holding : relatedHoldings) {
            logHoldingEvent(holding.getId(), "UPDATE",
                    "'" + holding.getName() + "': Значение поля 'Расположение' изменено с '" + locationName + "' на 'null' через справочник 'Расположения'",
                    "Расположение", locationName, null, author);
        }

        // Логируем в историю предприятий
        List<Enterprise> relatedEnterprises = enterpriseRepository.findByLocationUid(uid);
        for (Enterprise enterprise : relatedEnterprises) {
            logEnterpriseEvent(enterprise.getId(), "UPDATE",
                    "'" + enterprise.getName() + "': Значение поля 'Расположение' изменено с '" + locationName + "' на 'null' через справочник 'Расположения'",
                    "Расположение", locationName, null, author);
        }

        // Логируем в историю цехов
        List<Workshop> relatedWorkshops = workshopRepository.findAll().stream()
                .filter(w -> w.getLocation() != null && w.getLocation().getUid().equals(uid))
                .collect(Collectors.toList());
        for (Workshop workshop : relatedWorkshops) {
            logWorkshopEvent(workshop.getId(), "UPDATE",
                    "'" + workshop.getName() + "': Значение поля 'Расположение' изменено с '" + locationName + "' на 'null' через справочник 'Расположения'",
                    "Расположение", locationName, null, author);
        }

        logEvent(uid, "DELETE", "Удаление расположения: '" + locationName + "'", null, locationName, null, author);
        locationRepository.delete(location);
    }

    // ==================== EVENTS ====================

    public List<LocationEventLogDto> getEvents(UUID locationUid) {
        return eventLogRepository.findByLocationUidOrderByCreatedAtDesc(locationUid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<LocationEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== SETTINGS ====================

    public String getColumnsJson(Integer userId) {
        return columnSettingsRepository.findByUserId(userId)
                .map(UserLocationColumnSettings::getColumnsJson)
                .orElse(null);
    }

    public String getFiltersJson(Integer userId) {
        return columnSettingsRepository.findByUserId(userId)
                .map(UserLocationColumnSettings::getFiltersJson)
                .orElse("{}");
    }

    public String getSortJson(Integer userId) {
        return columnSettingsRepository.findByUserId(userId)
                .map(UserLocationColumnSettings::getSortJson)
                .orElse("{}");
    }

    @Transactional
    public void saveColumnsJson(Integer userId, String columnsJson) {
        UserLocationColumnSettings settings = getOrCreateSettings(userId);
        settings.setColumnsJson(columnsJson);
        columnSettingsRepository.save(settings);
    }

    @Transactional
    public void saveFiltersJson(Integer userId, String filtersJson) {
        UserLocationColumnSettings settings = getOrCreateSettings(userId);
        settings.setFiltersJson(filtersJson);
        columnSettingsRepository.save(settings);
    }

    @Transactional
    public void saveSortJson(Integer userId, String sortJson) {
        UserLocationColumnSettings settings = getOrCreateSettings(userId);
        settings.setSortJson(sortJson);
        columnSettingsRepository.save(settings);
    }

    private UserLocationColumnSettings getOrCreateSettings(Integer userId) {
        return columnSettingsRepository.findByUserId(userId)
                .orElseGet(() -> UserLocationColumnSettings.builder()
                        .userId(userId)
                        .columnsJson("{}")
                        .filtersJson("{}")
                        .sortJson("{}")
                        .build());
    }

    // ==================== PRIVATE ====================

    private void logEvent(UUID locationUid, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        LocationEventLog log = LocationEventLog.builder()
                .uid(UUID.randomUUID())
                .locationUid(locationUid)
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

    private void logHoldingEvent(Long holdingId, String eventType, String description,
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
        holdingEventLogRepository.save(log);
    }

    private void logEnterpriseEvent(Long enterpriseId, String eventType, String description,
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
        enterpriseEventLogRepository.save(log);
    }

    private void logWorkshopEvent(Long workshopId, String eventType, String description,
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
        workshopEventLogRepository.save(log);
    }

    private LocationEventLogDto toEventDTO(LocationEventLog e) {
        return LocationEventLogDto.builder()
                .uid(e.getUid())
                .locationUid(e.getLocationUid())
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

    private LocationFlatDto toDTO(Location location) {
        return LocationFlatDto.builder()
                .uid(location.getUid())
                .name(location.getName())
                .build();
    }
}
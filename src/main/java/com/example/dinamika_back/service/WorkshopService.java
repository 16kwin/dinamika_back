// WorkshopService.java — ПОЛНЫЙ ФАЙЛ (добавлено логирование в станции)
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
public class WorkshopService {

    private final WorkshopRepository workshopRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final LocationRepository locationRepository;
    private final WorkshopEventLogRepository eventLogRepository;
    private final WorkshopColumnSettingsService columnSettingsService;
    private final UserService userService;
    private final SectionRepository sectionRepository;
    private final SectionEventLogRepository sectionEventLogRepository;
    private final StationRepository stationRepository;
    private final StationEventLogRepository stationEventLogRepository;

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

        logEvent(workshop.getId(), "CREATE", "Создание цеха: '" + workshop.getName() + "'", null, null, null, userService.getCurrentUsername());

        return toDTO(workshop);
    }

    @Transactional
    public WorkshopFlatDto update(Long id, UpdateWorkshopRequest request) {
        Workshop workshop = workshopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Цех не найден: " + id));

        String author = userService.getCurrentUsername();

        Enterprise enterprise = enterpriseRepository.findById(request.getEnterpriseId())
                .orElseThrow(() -> new RuntimeException("Предприятие не найдено: " + request.getEnterpriseId()));

        if (!workshop.getName().equals(request.getName())
                || !workshop.getEnterprise().getId().equals(request.getEnterpriseId())) {
            if (workshopRepository.existsByNameAndEnterpriseId(request.getName(), request.getEnterpriseId())) {
                throw new RuntimeException("Цех с таким именем уже существует на этом предприятии");
            }
        }

        if (!workshop.getName().equals(request.getName())) {
            String oldName = workshop.getName();
            logEvent(id, "UPDATE", "'" + oldName + "': Значение поля 'Наименование' изменено с '" + oldName + "' на '" + request.getName() + "'",
                    "Наименование", oldName, request.getName(), author);
            workshop.setName(request.getName());

            // Логируем в историю секций
            List<Section> relatedSections = sectionRepository.findByWorkshopId(id);
            for (Section section : relatedSections) {
                logSectionEvent(section.getId(), "UPDATE",
                        "'" + section.getName() + "': Значение поля 'Цех' изменено с '" + oldName + "' на '" + request.getName() + "' через справочник 'Цеха'",
                        "Цех", oldName, request.getName(), author);
            }

            // Логируем в историю станций
            List<Station> relatedStations = stationRepository.findAll().stream()
                    .filter(s -> s.getWorkshop() != null && s.getWorkshop().getId().equals(id))
                    .collect(Collectors.toList());
            for (Station station : relatedStations) {
                logStationEvent(station.getUid(), "UPDATE",
                        "'" + station.getName() + "': Значение поля 'Цех' изменено с '" + oldName + "' на '" + request.getName() + "' через справочник 'Цеха'",
                        "Цех", oldName, request.getName(), author);
            }
        }

        if (request.getDescription() != null && !Objects.equals(request.getDescription(), workshop.getDescription())) {
            String currentName = workshop.getName();
            logEvent(id, "UPDATE", "'" + currentName + "': Значение поля 'Описание' изменено с '" + workshop.getDescription() + "' на '" + request.getDescription() + "'",
                    "Описание", workshop.getDescription(), request.getDescription(), author);
            workshop.setDescription(request.getDescription());
        }

        if (request.getAddress() != null && !Objects.equals(request.getAddress(), workshop.getAddress())) {
            String currentName = workshop.getName();
            logEvent(id, "UPDATE", "'" + currentName + "': Значение поля 'Адрес' изменено с '" + workshop.getAddress() + "' на '" + request.getAddress() + "'",
                    "Адрес", workshop.getAddress(), request.getAddress(), author);
            workshop.setAddress(request.getAddress());
        }

        if (!workshop.getEnterprise().getId().equals(request.getEnterpriseId())) {
            String currentName = workshop.getName();
            logEvent(id, "UPDATE", "'" + currentName + "': Значение поля 'Предприятие' изменено с '" + workshop.getEnterprise().getName() + "' на '" + enterprise.getName() + "'",
                    "Предприятие", workshop.getEnterprise().getName(), enterprise.getName(), author);
            workshop.setEnterprise(enterprise);
        }

        if (request.getLocationUid() != null) {
            String oldLocationName = workshop.getLocation() != null ? workshop.getLocation().getName() : null;
            Location location = locationRepository.findById(request.getLocationUid())
                    .orElseThrow(() -> new RuntimeException("Расположение не найдено: " + request.getLocationUid()));
            if (oldLocationName == null || !oldLocationName.equals(location.getName())) {
                String currentName = workshop.getName();
                logEvent(id, "UPDATE", "'" + currentName + "': Значение поля 'Расположение' изменено с '" + oldLocationName + "' на '" + location.getName() + "'",
                        "Расположение", oldLocationName, location.getName(), author);
                workshop.setLocation(location);
            }
        } else {
            if (workshop.getLocation() != null) {
                String currentName = workshop.getName();
                logEvent(id, "UPDATE", "'" + currentName + "': Значение поля 'Расположение' изменено с '" + workshop.getLocation().getName() + "' на 'null'",
                        "Расположение", workshop.getLocation().getName(), null, author);
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

        String author = userService.getCurrentUsername();

        // Логируем в историю секций
        List<Section> relatedSections = sectionRepository.findByWorkshopId(id);
        for (Section section : relatedSections) {
            logSectionEvent(section.getId(), "UPDATE",
                    "'" + section.getName() + "': Значение поля 'Цех' изменено с '" + workshop.getName() + "' на 'null' через справочник 'Цеха'",
                    "Цех", workshop.getName(), null, author);
        }

        // Логируем в историю станций
        List<Station> relatedStations = stationRepository.findAll().stream()
                .filter(s -> s.getWorkshop() != null && s.getWorkshop().getId().equals(id))
                .collect(Collectors.toList());
        for (Station station : relatedStations) {
            logStationEvent(station.getUid(), "UPDATE",
                    "'" + station.getName() + "': Значение поля 'Цех' изменено с '" + workshop.getName() + "' на 'null' через справочник 'Цеха'",
                    "Цех", workshop.getName(), null, author);
        }

        logEvent(id, "DELETE", "Удаление цеха: '" + workshop.getName() + "'", null, workshop.getName(), null, author);
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

    private void logSectionEvent(Long sectionId, String eventType, String description,
                                 String fieldName, String oldValue, String newValue, String author) {
        SectionEventLog log = SectionEventLog.builder()
                .uid(UUID.randomUUID())
                .sectionId(sectionId)
                .eventType(eventType)
                .eventDescription(description)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .author(author)
                .source("Через карточку")
                .createdAt(LocalDateTime.now())
                .build();
        sectionEventLogRepository.save(log);
    }

    private void logStationEvent(String stationUid, String eventType, String description,
                                 String fieldName, String oldValue, String newValue, String author) {
        StationEventLog log = StationEventLog.builder()
                .uid(UUID.randomUUID())
                .stationUid(stationUid)
                .eventType(eventType)
                .eventDescription(description)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .author(author)
                .source("Через карточку")
                .createdAt(LocalDateTime.now())
                .build();
        stationEventLogRepository.save(log);
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
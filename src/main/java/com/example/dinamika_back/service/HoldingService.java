// HoldingService.java — ПОЛНЫЙ ФАЙЛ (добавлено логирование в станции)
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
public class HoldingService {

    private final HoldingRepository holdingRepository;
    private final LocationRepository locationRepository;
    private final HoldingEventLogRepository eventLogRepository;
    private final HoldingColumnSettingsService columnSettingsService;
    private final UserService userService;
    private final EnterpriseRepository enterpriseRepository;
    private final EnterpriseEventLogRepository enterpriseEventLogRepository;
    private final WorkshopRepository workshopRepository;
    private final WorkshopEventLogRepository workshopEventLogRepository;
    private final SectionRepository sectionRepository;
    private final SectionEventLogRepository sectionEventLogRepository;
    private final StationRepository stationRepository;
    private final StationEventLogRepository stationEventLogRepository;

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

        logEvent(holding.getId(), "CREATE", "Создание холдинга: '" + holding.getName() + "'", null, null, null, userService.getCurrentUsername());

        return toDTO(holding);
    }

    @Transactional
    public HoldingFlatDto update(Long id, UpdateHoldingRequest request) {
        Holding holding = holdingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Холдинг не найден: " + id));

        String author = userService.getCurrentUsername();

        if (!holding.getName().equals(request.getName())
                && holdingRepository.existsByName(request.getName())) {
            throw new RuntimeException("Холдинг с таким именем уже существует: " + request.getName());
        }

        if (!holding.getName().equals(request.getName())) {
            String oldName = holding.getName();
            logEvent(id, "UPDATE", "'" + oldName + "': Значение поля 'Наименование' изменено с '" + oldName + "' на '" + request.getName() + "'",
                    "Наименование", oldName, request.getName(), author);
            holding.setName(request.getName());

            // Логируем в историю предприятий
            List<Enterprise> relatedEnterprises = enterpriseRepository.findByHoldingIdOrderByNameAsc(id);
            for (Enterprise enterprise : relatedEnterprises) {
                logEnterpriseEvent(enterprise.getId(), "UPDATE",
                        "'" + enterprise.getName() + "': Значение поля 'Холдинг' изменено с '" + oldName + "' на '" + request.getName() + "' через справочник 'Холдинги'",
                        "Холдинг", oldName, request.getName(), author);
            }

            // Логируем в историю цехов
            List<Workshop> relatedWorkshops = workshopRepository.findAll().stream()
                    .filter(w -> w.getEnterprise() != null && w.getEnterprise().getHolding() != null
                            && w.getEnterprise().getHolding().getId().equals(id))
                    .collect(Collectors.toList());
            for (Workshop workshop : relatedWorkshops) {
                logWorkshopEvent(workshop.getId(), "UPDATE",
                        "'" + workshop.getName() + "': Значение поля 'Холдинг' изменено с '" + oldName + "' на '" + request.getName() + "' через справочник 'Холдинги'",
                        "Холдинг", oldName, request.getName(), author);
            }

            // Логируем в историю секций
            List<Section> relatedSections = sectionRepository.findAll().stream()
                    .filter(s -> s.getWorkshop() != null && s.getWorkshop().getEnterprise() != null
                            && s.getWorkshop().getEnterprise().getHolding() != null
                            && s.getWorkshop().getEnterprise().getHolding().getId().equals(id))
                    .collect(Collectors.toList());
            for (Section section : relatedSections) {
                logSectionEvent(section.getId(), "UPDATE",
                        "'" + section.getName() + "': Значение поля 'Холдинг' изменено с '" + oldName + "' на '" + request.getName() + "' через справочник 'Холдинги'",
                        "Холдинг", oldName, request.getName(), author);
            }

            // Логируем в историю станций
            List<Station> relatedStations = stationRepository.findAll().stream()
                    .filter(s -> s.getHolding() != null && s.getHolding().getId().equals(id))
                    .collect(Collectors.toList());
            for (Station station : relatedStations) {
                logStationEvent(station.getUid(), "UPDATE",
                        "'" + station.getName() + "': Значение поля 'Холдинг' изменено с '" + oldName + "' на '" + request.getName() + "' через справочник 'Холдинги'",
                        "Холдинг", oldName, request.getName(), author);
            }
        }

        if (request.getDescription() != null && !request.getDescription().equals(holding.getDescription())) {
            String currentName = holding.getName();
            logEvent(id, "UPDATE", "'" + currentName + "': Значение поля 'Описание' изменено с '" + holding.getDescription() + "' на '" + request.getDescription() + "'",
                    "Описание", holding.getDescription(), request.getDescription(), author);
            holding.setDescription(request.getDescription());
        }

        if (request.getLocationUid() != null) {
            String oldLocationName = holding.getLocation() != null ? holding.getLocation().getName() : null;
            Location location = locationRepository.findById(request.getLocationUid())
                    .orElseThrow(() -> new RuntimeException("Расположение не найдено: " + request.getLocationUid()));
            if (oldLocationName == null || !oldLocationName.equals(location.getName())) {
                String currentName = holding.getName();
                logEvent(id, "UPDATE", "'" + currentName + "': Значение поля 'Расположение' изменено с '" + oldLocationName + "' на '" + location.getName() + "'",
                        "Расположение", oldLocationName, location.getName(), author);
                holding.setLocation(location);
            }
        } else {
            if (holding.getLocation() != null) {
                String currentName = holding.getName();
                logEvent(id, "UPDATE", "'" + currentName + "': Значение поля 'Расположение' изменено с '" + holding.getLocation().getName() + "' на 'null'",
                        "Расположение", holding.getLocation().getName(), null, author);
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

        String author = userService.getCurrentUsername();

        // Логируем в историю предприятий
        List<Enterprise> relatedEnterprises = enterpriseRepository.findByHoldingIdOrderByNameAsc(id);
        for (Enterprise enterprise : relatedEnterprises) {
            logEnterpriseEvent(enterprise.getId(), "UPDATE",
                    "'" + enterprise.getName() + "': Значение поля 'Холдинг' изменено с '" + holding.getName() + "' на 'null' через справочник 'Холдинги'",
                    "Холдинг", holding.getName(), null, author);
        }

        // Логируем в историю цехов
        List<Workshop> relatedWorkshops = workshopRepository.findAll().stream()
                .filter(w -> w.getEnterprise() != null && w.getEnterprise().getHolding() != null
                        && w.getEnterprise().getHolding().getId().equals(id))
                .collect(Collectors.toList());
        for (Workshop workshop : relatedWorkshops) {
            logWorkshopEvent(workshop.getId(), "UPDATE",
                    "'" + workshop.getName() + "': Значение поля 'Холдинг' изменено с '" + holding.getName() + "' на 'null' через справочник 'Холдинги'",
                    "Холдинг", holding.getName(), null, author);
        }

        // Логируем в историю секций
        List<Section> relatedSections = sectionRepository.findAll().stream()
                .filter(s -> s.getWorkshop() != null && s.getWorkshop().getEnterprise() != null
                        && s.getWorkshop().getEnterprise().getHolding() != null
                        && s.getWorkshop().getEnterprise().getHolding().getId().equals(id))
                .collect(Collectors.toList());
        for (Section section : relatedSections) {
            logSectionEvent(section.getId(), "UPDATE",
                    "'" + section.getName() + "': Значение поля 'Холдинг' изменено с '" + holding.getName() + "' на 'null' через справочник 'Холдинги'",
                    "Холдинг", holding.getName(), null, author);
        }

        // Логируем в историю станций
        List<Station> relatedStations = stationRepository.findAll().stream()
                .filter(s -> s.getHolding() != null && s.getHolding().getId().equals(id))
                .collect(Collectors.toList());
        for (Station station : relatedStations) {
            logStationEvent(station.getUid(), "UPDATE",
                    "'" + station.getName() + "': Значение поля 'Холдинг' изменено с '" + holding.getName() + "' на 'null' через справочник 'Холдинги'",
                    "Холдинг", holding.getName(), null, author);
        }

        logEvent(id, "DELETE", "Удаление холдинга: '" + holding.getName() + "'", null, holding.getName(), null, author);
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
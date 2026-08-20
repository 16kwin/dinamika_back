// EnterpriseService.java — ПОЛНЫЙ ФАЙЛ (добавлено логирование в станции)
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
public class EnterpriseService {

    private final EnterpriseRepository enterpriseRepository;
    private final HoldingRepository holdingRepository;
    private final LocationRepository locationRepository;
    private final EnterpriseEventLogRepository eventLogRepository;
    private final EnterpriseColumnSettingsService columnSettingsService;
    private final UserService userService;
    private final WorkshopRepository workshopRepository;
    private final WorkshopEventLogRepository workshopEventLogRepository;
    private final SectionRepository sectionRepository;
    private final SectionEventLogRepository sectionEventLogRepository;
    private final StationRepository stationRepository;
    private final StationEventLogRepository stationEventLogRepository;

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

        logEvent(enterprise.getId(), "CREATE", "Создание предприятия: '" + enterprise.getName() + "'", null, null, null, userService.getCurrentUsername());

        return toDTO(enterprise);
    }

    @Transactional
    public EnterpriseFlatDto update(Long id, UpdateEnterpriseRequest request) {
        Enterprise enterprise = enterpriseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Предприятие не найдено: " + id));

        String author = userService.getCurrentUsername();

        if (!enterprise.getName().equals(request.getName())
                && enterpriseRepository.existsByName(request.getName())) {
            throw new RuntimeException("Предприятие с таким именем уже существует: " + request.getName());
        }

        if (!enterprise.getName().equals(request.getName())) {
            String oldName = enterprise.getName();
            logEvent(id, "UPDATE", "'" + oldName + "': Значение поля 'Наименование' изменено с '" + oldName + "' на '" + request.getName() + "'",
                    "Наименование", oldName, request.getName(), author);
            enterprise.setName(request.getName());

            // Логируем в историю цехов
            List<Workshop> relatedWorkshops = workshopRepository.findByEnterpriseId(id);
            for (Workshop workshop : relatedWorkshops) {
                logWorkshopEvent(workshop.getId(), "UPDATE",
                        "'" + workshop.getName() + "': Значение поля 'Предприятие' изменено с '" + oldName + "' на '" + request.getName() + "' через справочник 'Предприятия'",
                        "Предприятие", oldName, request.getName(), author);
            }

            // Логируем в историю секций
            List<Section> relatedSections = sectionRepository.findAll().stream()
                    .filter(s -> s.getWorkshop() != null && s.getWorkshop().getEnterprise() != null
                            && s.getWorkshop().getEnterprise().getId().equals(id))
                    .collect(Collectors.toList());
            for (Section section : relatedSections) {
                logSectionEvent(section.getId(), "UPDATE",
                        "'" + section.getName() + "': Значение поля 'Предприятие' изменено с '" + oldName + "' на '" + request.getName() + "' через справочник 'Предприятия'",
                        "Предприятие", oldName, request.getName(), author);
            }

            // Логируем в историю станций
            List<Station> relatedStations = stationRepository.findAll().stream()
                    .filter(s -> s.getEnterprise() != null && s.getEnterprise().getId().equals(id))
                    .collect(Collectors.toList());
            for (Station station : relatedStations) {
                logStationEvent(station.getUid(), "UPDATE",
                        "'" + station.getName() + "': Значение поля 'Предприятие' изменено с '" + oldName + "' на '" + request.getName() + "' через справочник 'Предприятия'",
                        "Предприятие", oldName, request.getName(), author);
            }
        }

        if (request.getDescription() != null && !Objects.equals(request.getDescription(), enterprise.getDescription())) {
            String currentName = enterprise.getName();
            logEvent(id, "UPDATE", "'" + currentName + "': Значение поля 'Описание' изменено с '" + enterprise.getDescription() + "' на '" + request.getDescription() + "'",
                    "Описание", enterprise.getDescription(), request.getDescription(), author);
            enterprise.setDescription(request.getDescription());
        }

        if (request.getAddress() != null && !Objects.equals(request.getAddress(), enterprise.getAddress())) {
            String currentName = enterprise.getName();
            logEvent(id, "UPDATE", "'" + currentName + "': Значение поля 'Адрес' изменено с '" + enterprise.getAddress() + "' на '" + request.getAddress() + "'",
                    "Адрес", enterprise.getAddress(), request.getAddress(), author);
            enterprise.setAddress(request.getAddress());
        }

        if (request.getHoldingId() != null) {
            String oldHoldingName = enterprise.getHolding() != null ? enterprise.getHolding().getName() : null;
            Holding holding = holdingRepository.findById(request.getHoldingId())
                    .orElseThrow(() -> new RuntimeException("Холдинг не найден: " + request.getHoldingId()));
            if (oldHoldingName == null || !oldHoldingName.equals(holding.getName())) {
                String currentName = enterprise.getName();
                logEvent(id, "UPDATE", "'" + currentName + "': Значение поля 'Холдинг' изменено с '" + oldHoldingName + "' на '" + holding.getName() + "'",
                        "Холдинг", oldHoldingName, holding.getName(), author);
                enterprise.setHolding(holding);
            }
        } else {
            if (enterprise.getHolding() != null) {
                String currentName = enterprise.getName();
                logEvent(id, "UPDATE", "'" + currentName + "': Значение поля 'Холдинг' изменено с '" + enterprise.getHolding().getName() + "' на 'null'",
                        "Холдинг", enterprise.getHolding().getName(), null, author);
                enterprise.setHolding(null);
            }
        }

        if (request.getLocationUid() != null) {
            String oldLocationName = enterprise.getLocation() != null ? enterprise.getLocation().getName() : null;
            Location location = locationRepository.findById(request.getLocationUid())
                    .orElseThrow(() -> new RuntimeException("Расположение не найдено: " + request.getLocationUid()));
            if (oldLocationName == null || !oldLocationName.equals(location.getName())) {
                String currentName = enterprise.getName();
                logEvent(id, "UPDATE", "'" + currentName + "': Значение поля 'Расположение' изменено с '" + oldLocationName + "' на '" + location.getName() + "'",
                        "Расположение", oldLocationName, location.getName(), author);
                enterprise.setLocation(location);
            }
        } else {
            if (enterprise.getLocation() != null) {
                String currentName = enterprise.getName();
                logEvent(id, "UPDATE", "'" + currentName + "': Значение поля 'Расположение' изменено с '" + enterprise.getLocation().getName() + "' на 'null'",
                        "Расположение", enterprise.getLocation().getName(), null, author);
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

        String author = userService.getCurrentUsername();

        // Логируем в историю цехов
        List<Workshop> relatedWorkshops = workshopRepository.findByEnterpriseId(id);
        for (Workshop workshop : relatedWorkshops) {
            logWorkshopEvent(workshop.getId(), "UPDATE",
                    "'" + workshop.getName() + "': Значение поля 'Предприятие' изменено с '" + enterprise.getName() + "' на 'null' через справочник 'Предприятия'",
                    "Предприятие", enterprise.getName(), null, author);
        }

        // Логируем в историю секций
        List<Section> relatedSections = sectionRepository.findAll().stream()
                .filter(s -> s.getWorkshop() != null && s.getWorkshop().getEnterprise() != null
                        && s.getWorkshop().getEnterprise().getId().equals(id))
                .collect(Collectors.toList());
        for (Section section : relatedSections) {
            logSectionEvent(section.getId(), "UPDATE",
                    "'" + section.getName() + "': Значение поля 'Предприятие' изменено с '" + enterprise.getName() + "' на 'null' через справочник 'Предприятия'",
                    "Предприятие", enterprise.getName(), null, author);
        }

        // Логируем в историю станций
        List<Station> relatedStations = stationRepository.findAll().stream()
                .filter(s -> s.getEnterprise() != null && s.getEnterprise().getId().equals(id))
                .collect(Collectors.toList());
        for (Station station : relatedStations) {
            logStationEvent(station.getUid(), "UPDATE",
                    "'" + station.getName() + "': Значение поля 'Предприятие' изменено с '" + enterprise.getName() + "' на 'null' через справочник 'Предприятия'",
                    "Предприятие", enterprise.getName(), null, author);
        }

        logEvent(id, "DELETE", "Удаление предприятия: '" + enterprise.getName() + "'", null, enterprise.getName(), null, author);
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
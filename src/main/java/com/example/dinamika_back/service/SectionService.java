// SectionService.java — ПОЛНЫЙ ФАЙЛ (добавлено логирование в станции)
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
public class SectionService {

    private final SectionRepository sectionRepository;
    private final WorkshopRepository workshopRepository;
    private final SectionEventLogRepository eventLogRepository;
    private final SectionColumnSettingsService columnSettingsService;
    private final UserService userService;
    private final StationRepository stationRepository;
    private final StationEventLogRepository stationEventLogRepository;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of("name", "workshopName", "enterpriseName", "holdingName");
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("name"));

    // ==================== GET ALL WITH SETTINGS ====================

    public SectionListResponse getAllWithSettings(Integer userId) {
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

        List<SectionFlatDto> sections = sectionRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        SectionListResponse response = new SectionListResponse();
        response.setColumns(orderedColumns);
        response.setData(sections);
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

    public List<SectionFlatDto> getAll() {
        return sectionRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<SectionFlatDto> getByWorkshopId(Long workshopId) {
        return sectionRepository.findByWorkshopId(workshopId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public SectionFlatDto getById(Long id) {
        Section section = sectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Участок не найден: " + id));
        return toDTO(section);
    }

    @Transactional
    public SectionFlatDto create(CreateSectionRequest request) {
        Workshop workshop = workshopRepository.findById(request.getWorkshopId())
                .orElseThrow(() -> new RuntimeException("Цех не найден: " + request.getWorkshopId()));
        if (sectionRepository.existsByNameAndWorkshopId(request.getName(), request.getWorkshopId())) {
            throw new RuntimeException("Участок с таким именем уже существует в этом цехе");
        }
        Section section = new Section();
        section.setName(request.getName());
        section.setWorkshop(workshop);
        section = sectionRepository.save(section);

        logEvent(section.getId(), "CREATE", "Создание участка: '" + section.getName() + "'", null, null, null, userService.getCurrentUsername());

        return toDTO(section);
    }

    @Transactional
    public SectionFlatDto update(Long id, UpdateSectionRequest request) {
        Section section = sectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Участок не найден: " + id));

        String author = userService.getCurrentUsername();

        Workshop workshop = workshopRepository.findById(request.getWorkshopId())
                .orElseThrow(() -> new RuntimeException("Цех не найден: " + request.getWorkshopId()));

        if (!section.getName().equals(request.getName())
                || !section.getWorkshop().getId().equals(request.getWorkshopId())) {
            if (sectionRepository.existsByNameAndWorkshopId(request.getName(), request.getWorkshopId())) {
                throw new RuntimeException("Участок с таким именем уже существует в этом цехе");
            }
        }

        if (!section.getName().equals(request.getName())) {
            String oldName = section.getName();
            logEvent(id, "UPDATE", "'" + oldName + "': Значение поля 'Наименование' изменено с '" + oldName + "' на '" + request.getName() + "'",
                    "Наименование", oldName, request.getName(), author);
            section.setName(request.getName());

            // Логируем в историю станций
            List<Station> relatedStations = stationRepository.findAll().stream()
                    .filter(s -> s.getSection() != null && s.getSection().getId().equals(id))
                    .collect(Collectors.toList());
            for (Station station : relatedStations) {
                logStationEvent(station.getUid(), "UPDATE",
                        "'" + station.getName() + "': Значение поля 'Участок' изменено с '" + oldName + "' на '" + request.getName() + "' через справочник 'Участки'",
                        "Участок", oldName, request.getName(), author);
            }
        }

        if (!section.getWorkshop().getId().equals(request.getWorkshopId())) {
            String currentName = section.getName();
            String oldWorkshopName = section.getWorkshop().getName();
            
            String oldEnterpriseName = section.getWorkshop().getEnterprise() != null 
                    ? section.getWorkshop().getEnterprise().getName() : null;
            String newEnterpriseName = workshop.getEnterprise() != null 
                    ? workshop.getEnterprise().getName() : null;
            
            String oldHoldingName = section.getWorkshop().getEnterprise() != null 
                    && section.getWorkshop().getEnterprise().getHolding() != null
                    ? section.getWorkshop().getEnterprise().getHolding().getName() : null;
            String newHoldingName = workshop.getEnterprise() != null 
                    && workshop.getEnterprise().getHolding() != null
                    ? workshop.getEnterprise().getHolding().getName() : null;
            
            // Логируем изменение цеха
            logEvent(id, "UPDATE", "'" + currentName + "': Значение поля 'Цех' изменено с '" + oldWorkshopName + "' на '" + workshop.getName() + "'",
                    "Цех", oldWorkshopName, workshop.getName(), author);
            
            // Логируем изменение предприятия (если оно реально поменялось)
            if (oldEnterpriseName != null && newEnterpriseName != null 
                    && !oldEnterpriseName.equals(newEnterpriseName)) {
                logEvent(id, "UPDATE", "'" + currentName + "': Значение поля 'Предприятие' изменено с '" + oldEnterpriseName + "' на '" + newEnterpriseName + "'",
                        "Предприятие", oldEnterpriseName, newEnterpriseName, author);
            }
            
            // Логируем изменение холдинга (если он реально поменялся)
            if (oldHoldingName != null && newHoldingName != null 
                    && !oldHoldingName.equals(newHoldingName)) {
                logEvent(id, "UPDATE", "'" + currentName + "': Значение поля 'Холдинг' изменено с '" + oldHoldingName + "' на '" + newHoldingName + "'",
                        "Холдинг", oldHoldingName, newHoldingName, author);
            }
            
            section.setWorkshop(workshop);
        }

        section = sectionRepository.save(section);
        return toDTO(section);
    }

    @Transactional
    public void delete(Long id) {
        Section section = sectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Участок не найден: " + id));

        String author = userService.getCurrentUsername();

        // Логируем в историю станций
        List<Station> relatedStations = stationRepository.findAll().stream()
                .filter(s -> s.getSection() != null && s.getSection().getId().equals(id))
                .collect(Collectors.toList());
        for (Station station : relatedStations) {
            logStationEvent(station.getUid(), "UPDATE",
                    "'" + station.getName() + "': Значение поля 'Участок' изменено с '" + section.getName() + "' на 'null' через справочник 'Участки'",
                    "Участок", section.getName(), null, author);
        }

        logEvent(id, "DELETE", "Удаление участка: '" + section.getName() + "'", null, section.getName(), null, author);
        sectionRepository.delete(section);
    }

    // ==================== EVENTS ====================

    public List<SectionEventLogDto> getEvents(Long sectionId) {
        return eventLogRepository.findBySectionIdOrderByCreatedAtDesc(sectionId).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<SectionEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE ====================

    private void logEvent(Long sectionId, String eventType, String description,
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
        eventLogRepository.save(log);
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

    private SectionEventLogDto toEventDTO(SectionEventLog e) {
        return SectionEventLogDto.builder()
                .uid(e.getUid())
                .sectionId(e.getSectionId())
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

    private SectionFlatDto toDTO(Section section) {
        String workshopName = section.getWorkshop() != null ? section.getWorkshop().getName() : null;
        Long workshopId = section.getWorkshop() != null ? section.getWorkshop().getId() : null;
        Long enterpriseId = section.getWorkshop() != null && section.getWorkshop().getEnterprise() != null
                ? section.getWorkshop().getEnterprise().getId() : null;
        String enterpriseName = section.getWorkshop() != null && section.getWorkshop().getEnterprise() != null
                ? section.getWorkshop().getEnterprise().getName() : null;
        Long holdingId = section.getWorkshop() != null && section.getWorkshop().getEnterprise() != null
                && section.getWorkshop().getEnterprise().getHolding() != null
                ? section.getWorkshop().getEnterprise().getHolding().getId() : null;
        String holdingName = section.getWorkshop() != null && section.getWorkshop().getEnterprise() != null
                && section.getWorkshop().getEnterprise().getHolding() != null
                ? section.getWorkshop().getEnterprise().getHolding().getName() : null;

        return SectionFlatDto.builder()
                .id(section.getId())
                .name(section.getName())
                .holdingId(holdingId)
                .holdingName(holdingName)
                .enterpriseId(enterpriseId)
                .enterpriseName(enterpriseName)
                .workshopId(workshopId)
                .workshopName(workshopName)
                .build();
    }
}
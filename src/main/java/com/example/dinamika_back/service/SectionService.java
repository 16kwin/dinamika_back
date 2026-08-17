// SectionService.java — ПОЛНЫЙ ФАЙЛ (исправлен: добавлен columnSettingsService и getAllWithSettings)
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.Section;
import com.example.dinamika_back.model.SectionEventLog;
import com.example.dinamika_back.model.Workshop;
import com.example.dinamika_back.repository.SectionEventLogRepository;
import com.example.dinamika_back.repository.SectionRepository;
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
public class SectionService {

    private final SectionRepository sectionRepository;
    private final WorkshopRepository workshopRepository;
    private final SectionEventLogRepository eventLogRepository;
    private final SectionColumnSettingsService columnSettingsService;

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

        logEvent(section.getId(), "CREATE", "Создание участка", null, null, null, "Система");

        return toDTO(section);
    }

    @Transactional
    public SectionFlatDto update(Long id, UpdateSectionRequest request) {
        Section section = sectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Участок не найден: " + id));
        Workshop workshop = workshopRepository.findById(request.getWorkshopId())
                .orElseThrow(() -> new RuntimeException("Цех не найден: " + request.getWorkshopId()));

        if (!section.getName().equals(request.getName())
                || !section.getWorkshop().getId().equals(request.getWorkshopId())) {
            if (sectionRepository.existsByNameAndWorkshopId(request.getName(), request.getWorkshopId())) {
                throw new RuntimeException("Участок с таким именем уже существует в этом цехе");
            }
        }

        if (!section.getName().equals(request.getName())) {
            logFieldChange(id, "Наименование", section.getName(), request.getName(), "Система");
            section.setName(request.getName());
        }

        if (!section.getWorkshop().getId().equals(request.getWorkshopId())) {
            logFieldChange(id, "Цех", section.getWorkshop().getName(), workshop.getName(), "Система");
            section.setWorkshop(workshop);
        }

        section = sectionRepository.save(section);
        return toDTO(section);
    }

    @Transactional
    public void delete(Long id) {
        Section section = sectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Участок не найден: " + id));

        logEvent(id, "DELETE", "Удаление участка", null, section.getName(), null, "Система");
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

    private void logFieldChange(Long sectionId, String fieldName, String oldValue, String newValue, String author) {
        if (oldValue == null && newValue == null) return;
        if (oldValue != null && oldValue.equals(newValue)) return;

        if (oldValue == null && newValue != null) {
            logEvent(sectionId, "UPDATE", "Значение поля '" + fieldName + "' установлено: " + newValue,
                    fieldName, null, newValue, author);
        } else if (newValue == null && oldValue != null) {
            logEvent(sectionId, "UPDATE", "Значение поля '" + fieldName + "' очищено",
                    fieldName, oldValue, null, author);
        } else {
            logEvent(sectionId, "UPDATE", "Значение поля '" + fieldName + "' изменено с '" + oldValue + "' на '" + newValue + "'",
                    fieldName, oldValue, newValue, author);
        }
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
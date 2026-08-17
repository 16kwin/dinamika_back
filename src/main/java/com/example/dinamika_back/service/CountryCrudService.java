// CountryCrudService.java — ПОЛНЫЙ ФАЙЛ (добавлен getAllWithSettings)
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.CountryEventLogDto;
import com.example.dinamika_back.dto.CountryListResponse;
import com.example.dinamika_back.dto.SprCountryDTO;
import com.example.dinamika_back.model.CountryEventLog;
import com.example.dinamika_back.model.SprCountry;
import com.example.dinamika_back.model.UserCountryColumnSettings;
import com.example.dinamika_back.repository.CountryEventLogRepository;
import com.example.dinamika_back.repository.SprCountryRepository;
import com.example.dinamika_back.repository.UserCountryColumnSettingsRepository;
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
public class CountryCrudService {

    private final SprCountryRepository countryRepository;
    private final CountryEventLogRepository eventLogRepository;
    private final UserCountryColumnSettingsRepository columnSettingsRepository;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of("name");
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("name"));

    // ==================== GET ALL WITH SETTINGS ====================

    public CountryListResponse getAllWithSettings(Integer userId) {
        String columnsJson = columnSettingsRepository.findByUserId(userId)
                .map(UserCountryColumnSettings::getColumnsJson)
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

        List<SprCountryDTO> countries = countryRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new CountryListResponse(orderedColumns, countries, columnWidths, new ArrayList<>(requiredColumns));
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

    public List<SprCountryDTO> getAll() {
        return countryRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public SprCountryDTO getById(UUID uid) {
        SprCountry country = countryRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Страна не найдена: " + uid));
        return toDTO(country);
    }

    @Transactional
    public SprCountryDTO create(String name) {
        if (name == null || name.isBlank()) {
            throw new RuntimeException("Наименование страны обязательно");
        }
        SprCountry country = new SprCountry();
        country.setUid(UUID.randomUUID());
        country.setName(name);
        country = countryRepository.save(country);

        logEvent(country.getUid(), "CREATE", "Создание страны", null, null, null, "Система");

        return toDTO(country);
    }

    @Transactional
    public SprCountryDTO update(UUID uid, String name) {
        SprCountry country = countryRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Страна не найдена: " + uid));

        if (name != null && !name.isBlank() && !country.getName().equals(name)) {
            logFieldChange(uid, "Наименование", country.getName(), name, "Система");
            country.setName(name);
        }

        country = countryRepository.save(country);
        return toDTO(country);
    }

    @Transactional
    public void delete(UUID uid) {
        SprCountry country = countryRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Страна не найдена: " + uid));

        logEvent(uid, "DELETE", "Удаление страны", null, country.getName(), null, "Система");
        countryRepository.delete(country);
    }

    // ==================== EVENTS ====================

    public List<CountryEventLogDto> getEvents(UUID countryUid) {
        return eventLogRepository.findByCountryUidOrderByCreatedAtDesc(countryUid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<CountryEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== COLUMN SETTINGS ====================

    public String getColumnsJson(Integer userId) {
        return columnSettingsRepository.findByUserId(userId)
                .map(UserCountryColumnSettings::getColumnsJson)
                .orElse(null);
    }

    public String getFiltersJson(Integer userId) {
        return columnSettingsRepository.findByUserId(userId)
                .map(UserCountryColumnSettings::getFiltersJson)
                .orElse("{}");
    }

    public String getSortJson(Integer userId) {
        return columnSettingsRepository.findByUserId(userId)
                .map(UserCountryColumnSettings::getSortJson)
                .orElse("{}");
    }

    @Transactional
    public void saveColumnsJson(Integer userId, String columnsJson) {
        UserCountryColumnSettings settings = getOrCreateSettings(userId);
        settings.setColumnsJson(columnsJson);
        columnSettingsRepository.save(settings);
    }

    @Transactional
    public void saveFiltersJson(Integer userId, String filtersJson) {
        UserCountryColumnSettings settings = getOrCreateSettings(userId);
        settings.setFiltersJson(filtersJson);
        columnSettingsRepository.save(settings);
    }

    @Transactional
    public void saveSortJson(Integer userId, String sortJson) {
        UserCountryColumnSettings settings = getOrCreateSettings(userId);
        settings.setSortJson(sortJson);
        columnSettingsRepository.save(settings);
    }

    private UserCountryColumnSettings getOrCreateSettings(Integer userId) {
        return columnSettingsRepository.findByUserId(userId)
                .orElseGet(() -> UserCountryColumnSettings.builder()
                        .userId(userId)
                        .columnsJson("{}")
                        .filtersJson("{}")
                        .sortJson("{}")
                        .build());
    }

    // ==================== PRIVATE ====================

    private void logEvent(UUID countryUid, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        CountryEventLog log = CountryEventLog.builder()
                .uid(UUID.randomUUID())
                .countryUid(countryUid)
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

    private void logFieldChange(UUID countryUid, String fieldName, String oldValue, String newValue, String author) {
        if (oldValue == null && newValue == null) return;
        if (oldValue != null && oldValue.equals(newValue)) return;

        if (oldValue == null && newValue != null) {
            logEvent(countryUid, "UPDATE", "Значение поля '" + fieldName + "' установлено: " + newValue,
                    fieldName, null, newValue, author);
        } else if (newValue == null && oldValue != null) {
            logEvent(countryUid, "UPDATE", "Значение поля '" + fieldName + "' очищено",
                    fieldName, oldValue, null, author);
        } else {
            logEvent(countryUid, "UPDATE", "Значение поля '" + fieldName + "' изменено с '" + oldValue + "' на '" + newValue + "'",
                    fieldName, oldValue, newValue, author);
        }
    }

    private CountryEventLogDto toEventDTO(CountryEventLog e) {
        return CountryEventLogDto.builder()
                .uid(e.getUid())
                .countryUid(e.getCountryUid())
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

    private SprCountryDTO toDTO(SprCountry country) {
        return new SprCountryDTO(country.getUid(), country.getName());
    }
}
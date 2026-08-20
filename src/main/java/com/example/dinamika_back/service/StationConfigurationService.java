// StationConfigurationService.java — ПОЛНЫЙ ФАЙЛ (исправлена ошибка с lambda)
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
public class StationConfigurationService {

    private final StationConfigurationRepository configurationRepository;
    private final StationModelRepository modelRepository;
    private final StationConfigurationEventLogRepository eventLogRepository;
    private final StationConfigurationColumnSettingsService columnSettingsService;
    private final UserService userService;
    private final StationRepository stationRepository;
    private final StationEventLogRepository stationEventLogRepository;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of("name", "modelName");
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("name"));

    // ==================== GET ALL WITH SETTINGS ====================

    public StationConfigurationListResponse getAllWithSettings(Integer userId) {
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

        List<StationConfigurationDto> configs = configurationRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        StationConfigurationListResponse response = new StationConfigurationListResponse();
        response.setColumns(orderedColumns);
        response.setData(configs);
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

    public List<StationConfigurationDto> getAll() {
        return configurationRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<StationConfigurationDto> getByModelId(UUID modelId) {
        return configurationRepository.findByModelUidOrderByNameAsc(modelId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public StationConfigurationDto getById(UUID uid) {
        StationConfiguration config = configurationRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Конфигурация не найдена: " + uid));
        return toDTO(config);
    }

    @Transactional
    public StationConfigurationDto create(CreateStationConfigurationRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Наименование конфигурации обязательно");
        }
        if (request.getModelId() == null) {
            throw new RuntimeException("Модель станции обязательна");
        }

        StationModel model = modelRepository.findById(request.getModelId())
                .orElseThrow(() -> new RuntimeException("Модель станции не найдена: " + request.getModelId()));

        if (configurationRepository.existsByNameAndModelUid(request.getName(), request.getModelId())) {
            throw new RuntimeException("Конфигурация с таким именем уже существует для этой модели");
        }

        StationConfiguration config = new StationConfiguration();
        config.setUid(request.getUid() != null ? request.getUid() : UUID.randomUUID());
        config.setName(request.getName());
        config.setModel(model);
        config.setCellsStructure(request.getCellsStructure());

        config = configurationRepository.save(config);

        String author = userService.getCurrentUsername();
        logEvent(config.getUid(), "CREATE", "Создание конфигурации: '" + config.getName() + "'", null, null, null, author);
        
        // Логируем создание структуры ячеек в конфигурации
        if (config.getCellsStructure() != null && !config.getCellsStructure().isEmpty()) {
            logEvent(config.getUid(), "STRUCTURE_CREATE", "Создана структура ячеек", null, null, null, author);
            
            // Логируем в станции, которые используют эту конфигурацию
            final UUID configUid = config.getUid(); // Создаем final переменную
            List<Station> relatedStations = stationRepository.findAll().stream()
                    .filter(s -> s.getConfiguration() != null && s.getConfiguration().getUid().equals(configUid))
                    .collect(Collectors.toList());
            for (Station station : relatedStations) {
                logStationEvent(station.getUid(), "STRUCTURE_CREATE", "Создана структура ячеек конфигурации", null, null, null, author);
            }
        }

        return toDTO(config);
    }

    @Transactional
    public StationConfigurationDto update(UUID uid, UpdateStationConfigurationRequest request) {
        StationConfiguration config = configurationRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Конфигурация не найдена: " + uid));

        String author = userService.getCurrentUsername();

        if (request.getName() != null && !request.getName().isBlank()) {
            if (!config.getName().equals(request.getName())
                    && request.getModelId() != null
                    && configurationRepository.existsByNameAndModelUid(request.getName(), request.getModelId())) {
                throw new RuntimeException("Конфигурация с таким именем уже существует для этой модели");
            }
            if (!config.getName().equals(request.getName())) {
                String oldName = config.getName();
                logEvent(uid, "UPDATE", "'" + oldName + "': Значение поля 'Наименование' изменено с '" + oldName + "' на '" + request.getName() + "'",
                        "Наименование", oldName, request.getName(), author);
                config.setName(request.getName());

                // Логируем в историю станций
                List<Station> relatedStations = stationRepository.findAll().stream()
                        .filter(s -> s.getConfiguration() != null && s.getConfiguration().getUid().equals(uid))
                        .collect(Collectors.toList());
                for (Station station : relatedStations) {
                    logStationEvent(station.getUid(), "UPDATE",
                            "'" + station.getName() + "': Значение поля 'Конфигурация' изменено с '" + oldName + "' на '" + request.getName() + "' через справочник 'Конфигурации'",
                            "Конфигурация", oldName, request.getName(), author);
                }
            }
        }

        if (request.getModelId() != null) {
            String oldModelName = config.getModel() != null ? config.getModel().getName() : null;
            StationModel model = modelRepository.findById(request.getModelId())
                    .orElseThrow(() -> new RuntimeException("Модель станции не найдена: " + request.getModelId()));
            if (oldModelName == null || !oldModelName.equals(model.getName())) {
                String currentName = config.getName();
                logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'Модель станции' изменено с '" + oldModelName + "' на '" + model.getName() + "'",
                        "Модель станции", oldModelName, model.getName(), author);
                config.setModel(model);
            }
        }

        if (request.getCellsStructure() != null && !request.getCellsStructure().equals(config.getCellsStructure())) {
            logEvent(uid, "STRUCTURE_UPDATE", "Обновлена структура ячеек", null, null, null, author);
            
            // Логируем в станции, которые используют эту конфигурацию
            List<Station> relatedStations = stationRepository.findAll().stream()
                    .filter(s -> s.getConfiguration() != null && s.getConfiguration().getUid().equals(uid))
                    .collect(Collectors.toList());
            for (Station station : relatedStations) {
                logStationEvent(station.getUid(), "STRUCTURE_UPDATE", "Обновлена структура ячеек конфигурации", null, null, null, author);
            }
            
            config.setCellsStructure(request.getCellsStructure());
        }

        config = configurationRepository.save(config);
        return toDTO(config);
    }

    @Transactional
    public void delete(UUID uid) {
        StationConfiguration config = configurationRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Конфигурация не найдена: " + uid));

        String author = userService.getCurrentUsername();

        // Логируем в историю станций
        List<Station> relatedStations = stationRepository.findAll().stream()
                .filter(s -> s.getConfiguration() != null && s.getConfiguration().getUid().equals(uid))
                .collect(Collectors.toList());
        for (Station station : relatedStations) {
            logStationEvent(station.getUid(), "UPDATE",
                    "'" + station.getName() + "': Значение поля 'Конфигурация' изменено с '" + config.getName() + "' на 'null' через справочник 'Конфигурации'",
                    "Конфигурация", config.getName(), null, author);
        }

        logEvent(uid, "DELETE", "Удаление конфигурации: '" + config.getName() + "'", null, config.getName(), null, author);
        configurationRepository.delete(config);
    }

    // ==================== EVENTS ====================

    public List<StationConfigurationEventLogDto> getEvents(UUID stationConfigurationUid) {
        return eventLogRepository.findByStationConfigurationUidOrderByCreatedAtDesc(stationConfigurationUid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<StationConfigurationEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(e -> !"STRUCTURE_CREATE".equals(e.getEventType()) && !"STRUCTURE_UPDATE".equals(e.getEventType()))
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE ====================

    private void logEvent(UUID configUid, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        StationConfigurationEventLog log = StationConfigurationEventLog.builder()
                .uid(UUID.randomUUID())
                .stationConfigurationUid(configUid)
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

    private StationConfigurationEventLogDto toEventDTO(StationConfigurationEventLog e) {
        return StationConfigurationEventLogDto.builder()
                .uid(e.getUid())
                .stationConfigurationUid(e.getStationConfigurationUid())
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

    private StationConfigurationDto toDTO(StationConfiguration config) {
        return StationConfigurationDto.builder()
                .uid(config.getUid())
                .name(config.getName())
                .modelId(config.getModel() != null ? config.getModel().getUid() : null)
                .modelName(config.getModel() != null ? config.getModel().getName() : null)
                .cellsStructure(config.getCellsStructure())
                .build();
    }
}
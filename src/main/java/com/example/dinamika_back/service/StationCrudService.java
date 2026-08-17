// StationCrudService.java — ПОЛНЫЙ ФАЙЛ (с requiredColumns)
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
public class StationCrudService {

    private final StationRepository stationRepository;
    private final HoldingRepository holdingRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final WorkshopRepository workshopRepository;
    private final SectionRepository sectionRepository;
    private final StationModelRepository modelRepository;
    private final StationConfigurationRepository configurationRepository;
    private final StationColumnSettingsService columnSettingsService;
    private final StationEventLogRepository eventLogRepository;
    private final UserService userService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> ALL_COLUMNS_ORDER = List.of(
            "code", "name", "stationType", "modelName", "enterpriseName",
            "workshopName", "sectionName", "status", "uid", "description",
            "productionDate", "serialNumber", "article", "revision", "holdingName",
            "configurationName", "ipAddress", "networkPort", "activeTemplateName",
            "hasError", "isTmc", "isSgd", "isOk", "isAdditionalModule",
            "hasAdditionalModule", "parentUid"
    );

    // ==================== GET ALL ====================

    public StationListResponse getAll(Integer userId) {
        long startTotal = System.nanoTime();

        long startSettings = System.nanoTime();
        String columnsJson = columnSettingsService.getColumnsJson(userId);
        Set<String> visibleColumns = new LinkedHashSet<>();
        Map<String, Double> columnWidths = new HashMap<>();
        Set<String> requiredColumns = new LinkedHashSet<>();

        if (columnsJson != null && !columnsJson.isEmpty()) {
            parseColumnSettings(columnsJson, visibleColumns, columnWidths, requiredColumns);
        } else {
            visibleColumns = new LinkedHashSet<>(ALL_COLUMNS_ORDER);
        }
        long timeSettings = (System.nanoTime() - startSettings) / 1000;
        System.out.println("  [Этап 1] Получение настроек: " + timeSettings + " мкс");

        long startQuery = System.nanoTime();
        List<Station> stations = stationRepository.findAllWithRelations();
        long timeQuery = (System.nanoTime() - startQuery) / 1000;
        System.out.println("  [Этап 2] Запрос к БД (FULL): " + timeQuery + " мкс");

        long startBuild = System.nanoTime();
        
        List<String> orderedColumns = ALL_COLUMNS_ORDER.stream()
                .filter(visibleColumns::contains)
                .collect(Collectors.toList());

        List<Map<String, Object>> data = stations.stream()
                .map(this::buildFullRowData)
                .collect(Collectors.toList());
        
        long timeBuild = (System.nanoTime() - startBuild) / 1000;
        System.out.println("  [Этап 3] Формирование ответа: " + timeBuild + " мкс");

        StationListResponse response = new StationListResponse(orderedColumns, data, columnWidths);
        response.setRequiredColumns(new ArrayList<>(requiredColumns));

        long timeTotal = (System.nanoTime() - startTotal) / 1000;
        System.out.println("[StationCrudService.getAll] ИТОГО: " + timeTotal + " мкс (" + stations.size() + " станций, показано " + visibleColumns.size() + " колонок)");

        return response;
    }

    private void parseColumnSettings(String json, Set<String> visibleColumns, Map<String, Double> columnWidths, Set<String> requiredColumns) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            
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
            visibleColumns.addAll(ALL_COLUMNS_ORDER);
        }
    }

    // ==================== GET BY UID ====================

    public StationDto getByUid(String uid) {
        long start = System.nanoTime();
        Station station = stationRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Станция не найдена: " + uid));
        StationDto result = toDTO(station);
        long end = System.nanoTime();
        long durationMicros = (end - start) / 1000;
        System.out.println("[StationCrudService.getByUid] Время выполнения: " + durationMicros + " мкс (uid=" + uid + ")");
        return result;
    }

    // ==================== GENERATE CODE ====================

    public Integer generateCode() {
        Integer maxCode = stationRepository.findMaxCode();
        return maxCode != null ? maxCode + 1 : 1;
    }

    // ==================== CREATE ====================

    @Transactional
    public StationDto create(CreateStationRequest request) {
        long start = System.nanoTime();
        Station station = new Station();
        station.setUid(request.getUid());
        station.setCode(request.getUid() != null && stationRepository.existsByUid(request.getUid())
                ? (stationRepository.findMaxCode() != null ? stationRepository.findMaxCode() + 1 : 1)
                : generateCode());
        station.setName(request.getName());
        station.setDescription(request.getDescription());
        station.setProductionDate(request.getProductionDate());
        station.setSerialNumber(request.getSerialNumber());

        if (request.getHoldingId() != null) {
            Holding holding = holdingRepository.findById(request.getHoldingId())
                    .orElseThrow(() -> new RuntimeException("Холдинг не найден: " + request.getHoldingId()));
            station.setHolding(holding);
        }

        if (request.getEnterpriseId() != null) {
            Enterprise enterprise = enterpriseRepository.findById(request.getEnterpriseId())
                    .orElseThrow(() -> new RuntimeException("Предприятие не найдено: " + request.getEnterpriseId()));
            station.setEnterprise(enterprise);
        }

        if (request.getWorkshopId() != null) {
            Workshop workshop = workshopRepository.findById(request.getWorkshopId())
                    .orElseThrow(() -> new RuntimeException("Цех не найден: " + request.getWorkshopId()));
            station.setWorkshop(workshop);
        }

        if (request.getSectionId() != null) {
            Section section = sectionRepository.findById(request.getSectionId())
                    .orElseThrow(() -> new RuntimeException("Участок не найден: " + request.getSectionId()));
            station.setSection(section);
        }

        station.setStatus(request.getStatus() != null
                ? StationStatus.valueOf(request.getStatus())
                : StationStatus.WORKING);

        if (request.getModelId() != null) {
            StationModel model = modelRepository.findById(java.util.UUID.fromString(request.getModelId()))
                    .orElseThrow(() -> new RuntimeException("Модель не найдена: " + request.getModelId()));
            station.setModel(model);
        }

        if (request.getConfigurationUid() != null) {
            StationConfiguration config = configurationRepository.findById(java.util.UUID.fromString(request.getConfigurationUid()))
                    .orElseThrow(() -> new RuntimeException("Конфигурация не найдена: " + request.getConfigurationUid()));
            station.setConfiguration(config);
        }

        station.setParentUid(request.getParentUid());
        station.setIsAdditionalModule(request.getIsAdditionalModule() != null ? request.getIsAdditionalModule() : false);
        station.setHasAdditionalModule(request.getHasAdditionalModule() != null ? request.getHasAdditionalModule() : false);
        station.setHasError(request.getHasError() != null ? request.getHasError() : false);
        station.setIsTmc(request.getIsTmc() != null ? request.getIsTmc() : false);
        station.setIsSgd(request.getIsSgd() != null ? request.getIsSgd() : false);
        station.setIsOk(request.getIsOk() != null ? request.getIsOk() : false);
        station.setIpAddress(request.getIpAddress());
        station.setNetworkPort(request.getNetworkPort());

        station.setTotalCells(0);
        station.setFilledCells(0);
        station.setTemplateNomenclatureCount(0);
        station.setRemainingNomenclatureCount(0);
        station.setMaxReadyParts(0);
        station.setReadyPartsCount(0);

        station = stationRepository.save(station);

        logEvent(station.getUid(), "CREATE", "Создание станции", null, null, null, userService.getCurrentUsername());

        StationDto result = toDTO(station);
        long end = System.nanoTime();
        long durationMicros = (end - start) / 1000;
        System.out.println("[StationCrudService.create] Время выполнения: " + durationMicros + " мкс (uid=" + station.getUid() + ")");
        return result;
    }

    // ==================== UPDATE ====================

    @Transactional
    public StationDto update(String uid, UpdateStationRequest request) {
        long start = System.nanoTime();
        Station station = stationRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Станция не найдена: " + uid));

        String author = userService.getCurrentUsername();

        if (request.getName() != null && !request.getName().equals(station.getName())) {
            logFieldChange(uid, "Наименование", station.getName(), request.getName(), author);
            station.setName(request.getName());
        }
        if (request.getDescription() != null && !Objects.equals(request.getDescription(), station.getDescription())) {
            logFieldChange(uid, "Описание", station.getDescription(), request.getDescription(), author);
            station.setDescription(request.getDescription());
        }
        if (request.getProductionDate() != null && !Objects.equals(request.getProductionDate(), station.getProductionDate())) {
            logFieldChange(uid, "Дата производства", station.getProductionDate(), request.getProductionDate(), author);
            station.setProductionDate(request.getProductionDate());
        }
        if (request.getSerialNumber() != null && !Objects.equals(request.getSerialNumber(), station.getSerialNumber())) {
            logFieldChange(uid, "Серийный номер", station.getSerialNumber(), request.getSerialNumber(), author);
            station.setSerialNumber(request.getSerialNumber());
        }

        if (request.getStatus() != null) {
            String oldStatus = station.getStatus() != null ? station.getStatus().name() : null;
            if (!request.getStatus().equals(oldStatus)) {
                logFieldChange(uid, "Статус", oldStatus, request.getStatus(), author);
                station.setStatus(StationStatus.valueOf(request.getStatus()));
            }
        }

        if (request.getIpAddress() != null && !Objects.equals(request.getIpAddress(), station.getIpAddress())) {
            logFieldChange(uid, "IP-адрес", station.getIpAddress(), request.getIpAddress(), author);
            station.setIpAddress(request.getIpAddress());
        }
        if (request.getNetworkPort() != null && !Objects.equals(request.getNetworkPort(), station.getNetworkPort())) {
            logFieldChange(uid, "Порт", station.getNetworkPort(), request.getNetworkPort(), author);
            station.setNetworkPort(request.getNetworkPort());
        }
        if (request.getParentUid() != null && !Objects.equals(request.getParentUid(), station.getParentUid())) {
            logFieldChange(uid, "Родительская станция", station.getParentUid(), request.getParentUid(), author);
            station.setParentUid(request.getParentUid());
        }

        if (request.getIsAdditionalModule() != null && !Objects.equals(request.getIsAdditionalModule(), station.getIsAdditionalModule())) {
            logFieldChange(uid, "Доп. модуль", station.getIsAdditionalModule(), request.getIsAdditionalModule(), author);
            station.setIsAdditionalModule(request.getIsAdditionalModule());
        }
        if (request.getHasAdditionalModule() != null && !Objects.equals(request.getHasAdditionalModule(), station.getHasAdditionalModule())) {
            logFieldChange(uid, "Имеет доп. модуль", station.getHasAdditionalModule(), request.getHasAdditionalModule(), author);
            station.setHasAdditionalModule(request.getHasAdditionalModule());
        }
        if (request.getHasError() != null && !Objects.equals(request.getHasError(), station.getHasError())) {
            logFieldChange(uid, "Ошибка", station.getHasError(), request.getHasError(), author);
            station.setHasError(request.getHasError());
        }
        if (request.getIsTmc() != null && !Objects.equals(request.getIsTmc(), station.getIsTmc())) {
            logFieldChange(uid, "ТМЦ", station.getIsTmc(), request.getIsTmc(), author);
            station.setIsTmc(request.getIsTmc());
        }
        if (request.getIsSgd() != null && !Objects.equals(request.getIsSgd(), station.getIsSgd())) {
            logFieldChange(uid, "СГД", station.getIsSgd(), request.getIsSgd(), author);
            station.setIsSgd(request.getIsSgd());
        }
        if (request.getIsOk() != null && !Objects.equals(request.getIsOk(), station.getIsOk())) {
            logFieldChange(uid, "ОК", station.getIsOk(), request.getIsOk(), author);
            station.setIsOk(request.getIsOk());
        }

        if (request.getModelId() != null) {
            String oldModelId = station.getModel() != null ? station.getModel().getUid().toString() : null;
            if (!request.getModelId().equals(oldModelId)) {
                String oldModelName = station.getModel() != null ? station.getModel().getName() : null;
                StationModel newModel = modelRepository.findById(java.util.UUID.fromString(request.getModelId()))
                        .orElseThrow(() -> new RuntimeException("Модель не найдена: " + request.getModelId()));
                logFieldChange(uid, "Модель", oldModelName, newModel.getName(), author);
                station.setModel(newModel);
            }
        }

        if (request.getConfigurationUid() != null) {
            String oldConfigUid = station.getConfiguration() != null ? station.getConfiguration().getUid().toString() : null;
            if (!request.getConfigurationUid().equals(oldConfigUid)) {
                String oldConfigName = station.getConfiguration() != null ? station.getConfiguration().getName() : null;
                StationConfiguration newConfig = configurationRepository.findById(java.util.UUID.fromString(request.getConfigurationUid()))
                        .orElseThrow(() -> new RuntimeException("Конфигурация не найдена: " + request.getConfigurationUid()));
                logFieldChange(uid, "Конфигурация", oldConfigName, newConfig.getName(), author);
                station.setConfiguration(newConfig);
            }
        }

        if (request.getHoldingId() != null) {
            String oldHoldingName = station.getHolding() != null ? station.getHolding().getName() : null;
            Holding newHolding = holdingRepository.findById(request.getHoldingId())
                    .orElseThrow(() -> new RuntimeException("Холдинг не найден: " + request.getHoldingId()));
            if (!Objects.equals(newHolding.getName(), oldHoldingName)) {
                logFieldChange(uid, "Холдинг", oldHoldingName, newHolding.getName(), author);
                station.setHolding(newHolding);
            }
        }

        if (request.getEnterpriseId() != null) {
            String oldEnterpriseName = station.getEnterprise() != null ? station.getEnterprise().getName() : null;
            Enterprise newEnterprise = enterpriseRepository.findById(request.getEnterpriseId())
                    .orElseThrow(() -> new RuntimeException("Предприятие не найдено: " + request.getEnterpriseId()));
            if (!Objects.equals(newEnterprise.getName(), oldEnterpriseName)) {
                logFieldChange(uid, "Предприятие", oldEnterpriseName, newEnterprise.getName(), author);
                station.setEnterprise(newEnterprise);
            }
        }

        if (request.getWorkshopId() != null) {
            String oldWorkshopName = station.getWorkshop() != null ? station.getWorkshop().getName() : null;
            Workshop newWorkshop = workshopRepository.findById(request.getWorkshopId())
                    .orElseThrow(() -> new RuntimeException("Цех не найден: " + request.getWorkshopId()));
            if (!Objects.equals(newWorkshop.getName(), oldWorkshopName)) {
                logFieldChange(uid, "Цех", oldWorkshopName, newWorkshop.getName(), author);
                station.setWorkshop(newWorkshop);
            }
        }

        if (request.getSectionId() != null) {
            String oldSectionName = station.getSection() != null ? station.getSection().getName() : null;
            Section newSection = sectionRepository.findById(request.getSectionId())
                    .orElseThrow(() -> new RuntimeException("Участок не найден: " + request.getSectionId()));
            if (!Objects.equals(newSection.getName(), oldSectionName)) {
                logFieldChange(uid, "Участок", oldSectionName, newSection.getName(), author);
                station.setSection(newSection);
            }
        }

        station = stationRepository.save(station);
        StationDto result = toDTO(station);
        long end = System.nanoTime();
        long durationMicros = (end - start) / 1000;
        System.out.println("[StationCrudService.update] Время выполнения: " + durationMicros + " мкс (uid=" + uid + ")");
        return result;
    }

    // ==================== DELETE ====================

    @Transactional
    public void delete(String uid) {
        long start = System.nanoTime();
        Station station = stationRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Станция не найдена: " + uid));

        logEvent(uid, "DELETE", "Удаление станции", null, null, null, userService.getCurrentUsername());

        stationRepository.delete(station);
        long end = System.nanoTime();
        long durationMicros = (end - start) / 1000;
        System.out.println("[StationCrudService.delete] Время выполнения: " + durationMicros + " мкс (uid=" + uid + ")");
    }

    // ==================== EVENTS ====================

    public List<StationEventLogDto> getEvents(String stationUid) {
        return eventLogRepository.findByStationUidOrderByCreatedAtDesc(stationUid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<StationEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE: LOGGING ====================

    private void logEvent(String stationUid, String eventType, String description,
                         String fieldName, String oldValue, String newValue, String author) {
        StationEventLog log = StationEventLog.builder()
                .uid(java.util.UUID.randomUUID())
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
        eventLogRepository.save(log);
    }

    private void logFieldChange(String stationUid, String fieldName, Object oldValue, Object newValue, String author) {
        String oldStr = oldValue != null ? oldValue.toString() : null;
        String newStr = newValue != null ? newValue.toString() : null;

        if (oldStr == null && newStr == null) return;
        if (oldStr != null && oldStr.equals(newStr)) return;

        if (oldStr == null && newStr != null) {
            logEvent(stationUid, "UPDATE", "Значение поля '" + fieldName + "' установлено: " + newStr,
                    fieldName, null, newStr, author);
        } else if (newStr == null && oldStr != null) {
            logEvent(stationUid, "UPDATE", "Значение поля '" + fieldName + "' очищено",
                    fieldName, oldStr, null, author);
        } else {
            logEvent(stationUid, "UPDATE", "Значение поля '" + fieldName + "' изменено с '" + oldStr + "' на '" + newStr + "'",
                    fieldName, oldStr, newStr, author);
        }
    }

    // ==================== PRIVATE: DTO / MAPPING ====================

    private StationEventLogDto toEventDTO(StationEventLog e) {
        return StationEventLogDto.builder()
                .uid(e.getUid())
                .stationUid(e.getStationUid())
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

    private StationDto toDTO(Station station) {
        StationDto dto = new StationDto();
        dto.setUid(station.getUid());
        dto.setCode(station.getCode());
        dto.setName(station.getName());
        dto.setDescription(station.getDescription());
        dto.setProductionDate(station.getProductionDate());
        dto.setSerialNumber(station.getSerialNumber());

        if (station.getModel() != null) {
            dto.setModelId(station.getModel().getUid().toString());
            dto.setModelName(station.getModel().getName());
            dto.setArticle(station.getModel().getArticle());
            dto.setRevision(station.getModel().getRevision());
            if (station.getModel().getType() != null) {
                dto.setStationType(station.getModel().getType().getName());
                dto.setStationTypeUid(station.getModel().getType().getUid().toString());
            }
        }

        dto.setHoldingId(station.getHolding() != null ? station.getHolding().getId() : null);
        dto.setHoldingName(station.getHolding() != null ? station.getHolding().getName() : null);
        dto.setEnterpriseId(station.getEnterprise() != null ? station.getEnterprise().getId() : null);
        dto.setEnterpriseName(station.getEnterprise() != null ? station.getEnterprise().getName() : null);
        dto.setWorkshopId(station.getWorkshop() != null ? station.getWorkshop().getId() : null);
        dto.setWorkshopName(station.getWorkshop() != null ? station.getWorkshop().getName() : null);
        dto.setSectionId(station.getSection() != null ? station.getSection().getId() : null);
        dto.setSectionName(station.getSection() != null ? station.getSection().getName() : null);

        dto.setStatus(station.getStatus() != null ? station.getStatus().name() : null);

        if (station.getConfiguration() != null) {
            dto.setConfigurationUid(station.getConfiguration().getUid().toString());
            dto.setConfigurationName(station.getConfiguration().getName());
        }

        dto.setParentUid(station.getParentUid());
        dto.setIsAdditionalModule(station.getIsAdditionalModule());
        dto.setHasAdditionalModule(station.getHasAdditionalModule());
        dto.setHasError(station.getHasError());
        dto.setIsTmc(station.getIsTmc());
        dto.setIsSgd(station.getIsSgd());
        dto.setIsOk(station.getIsOk());
        dto.setIpAddress(station.getIpAddress());
        dto.setNetworkPort(station.getNetworkPort());

        dto.setActiveTemplateUid(station.getActiveTemplate() != null ? station.getActiveTemplate().getUid().toString() : null);
        dto.setActiveTemplateName(station.getActiveTemplate() != null ? station.getActiveTemplate().getNamePattern() : null);

        return dto;
    }

    private Map<String, Object> buildFullRowData(Station station) {
        Map<String, Object> row = new LinkedHashMap<>();
        
        row.put("uid", station.getUid());
        row.put("code", station.getCode());
        row.put("name", station.getName());
        row.put("description", station.getDescription());
        row.put("productionDate", station.getProductionDate());
        row.put("serialNumber", station.getSerialNumber());
        row.put("status", station.getStatus() != null ? station.getStatus().name() : null);
        row.put("ipAddress", station.getIpAddress());
        row.put("networkPort", station.getNetworkPort());
        row.put("parentUid", station.getParentUid());
        row.put("hasError", station.getHasError());
        row.put("isTmc", station.getIsTmc());
        row.put("isSgd", station.getIsSgd());
        row.put("isOk", station.getIsOk());
        row.put("isAdditionalModule", station.getIsAdditionalModule());
        row.put("hasAdditionalModule", station.getHasAdditionalModule());

        StationModel model = station.getModel();
        if (model != null) {
            row.put("modelId", model.getUid().toString());
            row.put("modelName", model.getName());
            row.put("article", model.getArticle());
            row.put("revision", model.getRevision());
            if (model.getType() != null) {
                row.put("stationTypeUid", model.getType().getUid().toString());
                row.put("stationType", model.getType().getName());
            } else {
                row.put("stationTypeUid", null);
                row.put("stationType", null);
            }
        } else {
            row.put("modelId", null);
            row.put("modelName", null);
            row.put("stationTypeUid", null);
            row.put("stationType", null);
            row.put("article", null);
            row.put("revision", null);
        }

        Holding holding = station.getHolding();
        if (holding != null) {
            row.put("holdingId", holding.getId());
            row.put("holdingName", holding.getName());
        } else {
            row.put("holdingId", null);
            row.put("holdingName", null);
        }

        Enterprise enterprise = station.getEnterprise();
        if (enterprise != null) {
            row.put("enterpriseId", enterprise.getId());
            row.put("enterpriseName", enterprise.getName());
        } else {
            row.put("enterpriseId", null);
            row.put("enterpriseName", null);
        }

        Workshop workshop = station.getWorkshop();
        if (workshop != null) {
            row.put("workshopId", workshop.getId());
            row.put("workshopName", workshop.getName());
        } else {
            row.put("workshopId", null);
            row.put("workshopName", null);
        }

        Section section = station.getSection();
        if (section != null) {
            row.put("sectionId", section.getId());
            row.put("sectionName", section.getName());
        } else {
            row.put("sectionId", null);
            row.put("sectionName", null);
        }

        StationConfiguration config = station.getConfiguration();
        if (config != null) {
            row.put("configurationUid", config.getUid().toString());
            row.put("configurationName", config.getName());
        } else {
            row.put("configurationUid", null);
            row.put("configurationName", null);
        }

        DocPattern template = station.getActiveTemplate();
        if (template != null) {
            row.put("activeTemplateUid", template.getUid().toString());
            row.put("activeTemplateName", template.getNamePattern());
        } else {
            row.put("activeTemplateUid", null);
            row.put("activeTemplateName", null);
        }

        return row;
    }
}
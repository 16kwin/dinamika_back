// StationCrudService.java — ПОЛНЫЙ ФАЙЛ (copy возвращает данные и документы)
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
    private final StationDocumentRepository stationDocumentRepository;

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
        String columnsJson = columnSettingsService.getColumnsJson(userId);
        Set<String> visibleColumns = new LinkedHashSet<>();
        Map<String, Double> columnWidths = new HashMap<>();
        Set<String> requiredColumns = new LinkedHashSet<>();

        if (columnsJson != null && !columnsJson.isEmpty()) {
            parseColumnSettings(columnsJson, visibleColumns, columnWidths, requiredColumns);
        } else {
            visibleColumns = new LinkedHashSet<>(ALL_COLUMNS_ORDER);
        }

        List<String> orderedColumns = ALL_COLUMNS_ORDER.stream()
                .filter(visibleColumns::contains)
                .collect(Collectors.toList());

        List<Station> stations = stationRepository.findAllWithRelations();

        List<Map<String, Object>> data = stations.stream()
                .map(this::buildFullRowData)
                .collect(Collectors.toList());

        StationListResponse response = new StationListResponse(orderedColumns, data, columnWidths);
        response.setRequiredColumns(new ArrayList<>(requiredColumns));

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
        Station station = stationRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Станция не найдена: " + uid));
        return toDTO(station);
    }

    // ==================== GENERATE CODE ====================

    public Integer generateCode() {
        Integer maxCode = stationRepository.findMaxCode();
        return maxCode != null ? maxCode + 1 : 1;
    }

    // ==================== CREATE ====================

    @Transactional
    public StationDto create(CreateStationRequest request) {
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

        logEvent(station.getUid(), "CREATE", "Создание станции: '" + station.getName() + "'", null, null, null, userService.getCurrentUsername());

        return toDTO(station);
    }

    // ==================== COPY (без сохранения) ====================

    public StationDto copy(String uid) {
        Station original = stationRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Станция не найдена: " + uid));

        StationDto copyDto = new StationDto();
        copyDto.setUid(java.util.UUID.randomUUID().toString());
        copyDto.setCode(generateCode());
        copyDto.setName(original.getName() + " (копия)");
        copyDto.setDescription(original.getDescription());
        copyDto.setProductionDate(original.getProductionDate());
        copyDto.setSerialNumber(original.getSerialNumber());

        copyDto.setHoldingId(original.getHolding() != null ? original.getHolding().getId() : null);
        copyDto.setHoldingName(original.getHolding() != null ? original.getHolding().getName() : null);
        copyDto.setEnterpriseId(original.getEnterprise() != null ? original.getEnterprise().getId() : null);
        copyDto.setEnterpriseName(original.getEnterprise() != null ? original.getEnterprise().getName() : null);
        copyDto.setWorkshopId(original.getWorkshop() != null ? original.getWorkshop().getId() : null);
        copyDto.setWorkshopName(original.getWorkshop() != null ? original.getWorkshop().getName() : null);
        copyDto.setSectionId(original.getSection() != null ? original.getSection().getId() : null);
        copyDto.setSectionName(original.getSection() != null ? original.getSection().getName() : null);

        copyDto.setStatus(original.getStatus() != null ? original.getStatus().name() : null);

        if (original.getModel() != null) {
            copyDto.setModelId(original.getModel().getUid().toString());
            copyDto.setModelName(original.getModel().getName());
            copyDto.setArticle(original.getModel().getArticle());
            copyDto.setRevision(original.getModel().getRevision());
            if (original.getModel().getType() != null) {
                copyDto.setStationType(original.getModel().getType().getName());
                copyDto.setStationTypeUid(original.getModel().getType().getUid().toString());
            }
        }

        if (original.getConfiguration() != null) {
            copyDto.setConfigurationUid(original.getConfiguration().getUid().toString());
            copyDto.setConfigurationName(original.getConfiguration().getName());
        }

        copyDto.setParentUid(original.getParentUid());
        copyDto.setIsAdditionalModule(original.getIsAdditionalModule());
        copyDto.setHasAdditionalModule(original.getHasAdditionalModule());
        copyDto.setHasError(original.getHasError());
        copyDto.setIsTmc(original.getIsTmc());
        copyDto.setIsSgd(original.getIsSgd());
        copyDto.setIsOk(original.getIsOk());
        copyDto.setIpAddress(original.getIpAddress());
        copyDto.setNetworkPort(original.getNetworkPort());

        if (original.getActiveTemplate() != null) {
            copyDto.setActiveTemplateUid(original.getActiveTemplate().getUid().toString());
            copyDto.setActiveTemplateName(original.getActiveTemplate().getNamePattern());
        }

        // Копируем документы (метаданные) для передачи на фронтенд
        List<StationDocument> docs = stationDocumentRepository.findByStationUidOrderByCreatedAtDesc(original.getUid());
        List<StationDocumentDto> docDtos = docs.stream()
                .map(doc -> StationDocumentDto.builder()
                        .uid(doc.getUid())
                        .stationUid(doc.getStationUid())
                        .documentName(doc.getDocumentName())
                        .filePath(doc.getFilePath())
                        .originalName(doc.getOriginalName())
                        .createdAt(doc.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
        copyDto.setDocuments(docDtos);

        return copyDto;
    }

    // ==================== UPDATE ====================

    @Transactional
    public StationDto update(String uid, UpdateStationRequest request) {
        Station station = stationRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Станция не найдена: " + uid));

        String author = userService.getCurrentUsername();

        if (request.getName() != null && !request.getName().equals(station.getName())) {
            String oldName = station.getName();
            logEvent(uid, "UPDATE", "'" + oldName + "': Значение поля 'Наименование' изменено с '" + oldName + "' на '" + request.getName() + "'",
                    "Наименование", oldName, request.getName(), author);
            station.setName(request.getName());
        }
        if (request.getDescription() != null && !Objects.equals(request.getDescription(), station.getDescription())) {
            String currentName = station.getName();
            String oldVal = station.getDescription() != null ? station.getDescription() : "null";
            String newVal = request.getDescription() != null ? request.getDescription() : "null";
            logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'Описание' изменено с '" + oldVal + "' на '" + newVal + "'",
                    "Описание", oldVal, newVal, author);
            station.setDescription(request.getDescription());
        }
        if (request.getProductionDate() != null && !Objects.equals(request.getProductionDate(), station.getProductionDate())) {
            String currentName = station.getName();
            String oldVal = station.getProductionDate() != null ? station.getProductionDate().toString() : "null";
            String newVal = request.getProductionDate() != null ? request.getProductionDate().toString() : "null";
            logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'Дата производства' изменено с '" + oldVal + "' на '" + newVal + "'",
                    "Дата производства", oldVal, newVal, author);
            station.setProductionDate(request.getProductionDate());
        }
        if (request.getSerialNumber() != null && !Objects.equals(request.getSerialNumber(), station.getSerialNumber())) {
            String currentName = station.getName();
            String oldVal = station.getSerialNumber() != null ? station.getSerialNumber() : "null";
            String newVal = request.getSerialNumber() != null ? request.getSerialNumber() : "null";
            logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'Серийный номер' изменено с '" + oldVal + "' на '" + newVal + "'",
                    "Серийный номер", oldVal, newVal, author);
            station.setSerialNumber(request.getSerialNumber());
        }

        if (request.getStatus() != null) {
            String oldStatus = station.getStatus() != null ? station.getStatus().name() : null;
            if (!request.getStatus().equals(oldStatus)) {
                String currentName = station.getName();
                logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'Статус' изменено с '" + oldStatus + "' на '" + request.getStatus() + "'",
                        "Статус", oldStatus, request.getStatus(), author);
                station.setStatus(StationStatus.valueOf(request.getStatus()));
            }
        }

        if (request.getIpAddress() != null && !Objects.equals(request.getIpAddress(), station.getIpAddress())) {
            String currentName = station.getName();
            String oldVal = station.getIpAddress() != null ? station.getIpAddress() : "null";
            String newVal = request.getIpAddress() != null ? request.getIpAddress() : "null";
            logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'IP-адрес' изменено с '" + oldVal + "' на '" + newVal + "'",
                    "IP-адрес", oldVal, newVal, author);
            station.setIpAddress(request.getIpAddress());
        }
        if (request.getNetworkPort() != null && !Objects.equals(request.getNetworkPort(), station.getNetworkPort())) {
            String currentName = station.getName();
            String oldVal = station.getNetworkPort() != null ? String.valueOf(station.getNetworkPort()) : "null";
            String newVal = request.getNetworkPort() != null ? String.valueOf(request.getNetworkPort()) : "null";
            logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'Порт' изменено с '" + oldVal + "' на '" + newVal + "'",
                    "Порт", oldVal, newVal, author);
            station.setNetworkPort(request.getNetworkPort());
        }
        if (request.getParentUid() != null && !Objects.equals(request.getParentUid(), station.getParentUid())) {
            String currentName = station.getName();
            String oldVal = station.getParentUid() != null ? station.getParentUid() : "null";
            String newVal = request.getParentUid() != null ? request.getParentUid() : "null";
            logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'Родительская станция' изменено с '" + oldVal + "' на '" + newVal + "'",
                    "Родительская станция", oldVal, newVal, author);
            station.setParentUid(request.getParentUid());
        }

        if (request.getIsAdditionalModule() != null && !Objects.equals(request.getIsAdditionalModule(), station.getIsAdditionalModule())) {
            String currentName = station.getName();
            String oldVal = String.valueOf(station.getIsAdditionalModule());
            String newVal = String.valueOf(request.getIsAdditionalModule());
            logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'Доп. модуль' изменено с '" + oldVal + "' на '" + newVal + "'",
                    "Доп. модуль", oldVal, newVal, author);
            station.setIsAdditionalModule(request.getIsAdditionalModule());
        }
        if (request.getHasAdditionalModule() != null && !Objects.equals(request.getHasAdditionalModule(), station.getHasAdditionalModule())) {
            String currentName = station.getName();
            String oldVal = String.valueOf(station.getHasAdditionalModule());
            String newVal = String.valueOf(request.getHasAdditionalModule());
            logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'Имеет доп. модуль' изменено с '" + oldVal + "' на '" + newVal + "'",
                    "Имеет доп. модуль", oldVal, newVal, author);
            station.setHasAdditionalModule(request.getHasAdditionalModule());
        }
        if (request.getHasError() != null && !Objects.equals(request.getHasError(), station.getHasError())) {
            String currentName = station.getName();
            String oldVal = String.valueOf(station.getHasError());
            String newVal = String.valueOf(request.getHasError());
            logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'Ошибка' изменено с '" + oldVal + "' на '" + newVal + "'",
                    "Ошибка", oldVal, newVal, author);
            station.setHasError(request.getHasError());
        }
        if (request.getIsTmc() != null && !Objects.equals(request.getIsTmc(), station.getIsTmc())) {
            String currentName = station.getName();
            String oldVal = String.valueOf(station.getIsTmc());
            String newVal = String.valueOf(request.getIsTmc());
            logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'ТМЦ' изменено с '" + oldVal + "' на '" + newVal + "'",
                    "ТМЦ", oldVal, newVal, author);
            station.setIsTmc(request.getIsTmc());
        }
        if (request.getIsSgd() != null && !Objects.equals(request.getIsSgd(), station.getIsSgd())) {
            String currentName = station.getName();
            String oldVal = String.valueOf(station.getIsSgd());
            String newVal = String.valueOf(request.getIsSgd());
            logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'СГД' изменено с '" + oldVal + "' на '" + newVal + "'",
                    "СГД", oldVal, newVal, author);
            station.setIsSgd(request.getIsSgd());
        }
        if (request.getIsOk() != null && !Objects.equals(request.getIsOk(), station.getIsOk())) {
            String currentName = station.getName();
            String oldVal = String.valueOf(station.getIsOk());
            String newVal = String.valueOf(request.getIsOk());
            logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'ОК' изменено с '" + oldVal + "' на '" + newVal + "'",
                    "ОК", oldVal, newVal, author);
            station.setIsOk(request.getIsOk());
        }

        if (request.getModelId() != null) {
            String oldModelId = station.getModel() != null ? station.getModel().getUid().toString() : null;
            if (!request.getModelId().equals(oldModelId)) {
                String oldModelName = station.getModel() != null ? station.getModel().getName() : "null";
                StationModel newModel = modelRepository.findById(java.util.UUID.fromString(request.getModelId()))
                        .orElseThrow(() -> new RuntimeException("Модель не найдена: " + request.getModelId()));
                String currentName = station.getName();
                logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'Модель' изменено с '" + oldModelName + "' на '" + newModel.getName() + "'",
                        "Модель", oldModelName, newModel.getName(), author);
                station.setModel(newModel);
            }
        }

        if (request.getConfigurationUid() != null) {
            String oldConfigUid = station.getConfiguration() != null ? station.getConfiguration().getUid().toString() : null;
            if (!request.getConfigurationUid().equals(oldConfigUid)) {
                String oldConfigName = station.getConfiguration() != null ? station.getConfiguration().getName() : "null";
                StationConfiguration newConfig = configurationRepository.findById(java.util.UUID.fromString(request.getConfigurationUid()))
                        .orElseThrow(() -> new RuntimeException("Конфигурация не найдена: " + request.getConfigurationUid()));
                String currentName = station.getName();
                logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'Конфигурация' изменено с '" + oldConfigName + "' на '" + newConfig.getName() + "'",
                        "Конфигурация", oldConfigName, newConfig.getName(), author);
                station.setConfiguration(newConfig);
            }
        }

        if (request.getHoldingId() != null) {
            String oldHoldingName = station.getHolding() != null ? station.getHolding().getName() : "null";
            Holding newHolding = holdingRepository.findById(request.getHoldingId())
                    .orElseThrow(() -> new RuntimeException("Холдинг не найден: " + request.getHoldingId()));
            if (!Objects.equals(newHolding.getName(), oldHoldingName)) {
                String currentName = station.getName();
                logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'Холдинг' изменено с '" + oldHoldingName + "' на '" + newHolding.getName() + "'",
                        "Холдинг", oldHoldingName, newHolding.getName(), author);
                station.setHolding(newHolding);
            }
        }

        if (request.getEnterpriseId() != null) {
            String oldEnterpriseName = station.getEnterprise() != null ? station.getEnterprise().getName() : "null";
            Enterprise newEnterprise = enterpriseRepository.findById(request.getEnterpriseId())
                    .orElseThrow(() -> new RuntimeException("Предприятие не найдено: " + request.getEnterpriseId()));
            if (!Objects.equals(newEnterprise.getName(), oldEnterpriseName)) {
                String currentName = station.getName();
                logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'Предприятие' изменено с '" + oldEnterpriseName + "' на '" + newEnterprise.getName() + "'",
                        "Предприятие", oldEnterpriseName, newEnterprise.getName(), author);
                station.setEnterprise(newEnterprise);
            }
        }

        if (request.getWorkshopId() != null) {
            String oldWorkshopName = station.getWorkshop() != null ? station.getWorkshop().getName() : "null";
            Workshop newWorkshop = workshopRepository.findById(request.getWorkshopId())
                    .orElseThrow(() -> new RuntimeException("Цех не найден: " + request.getWorkshopId()));
            if (!Objects.equals(newWorkshop.getName(), oldWorkshopName)) {
                String currentName = station.getName();
                logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'Цех' изменено с '" + oldWorkshopName + "' на '" + newWorkshop.getName() + "'",
                        "Цех", oldWorkshopName, newWorkshop.getName(), author);
                station.setWorkshop(newWorkshop);
            }
        }

        if (request.getSectionId() != null) {
            String oldSectionName = station.getSection() != null ? station.getSection().getName() : "null";
            Section newSection = sectionRepository.findById(request.getSectionId())
                    .orElseThrow(() -> new RuntimeException("Участок не найден: " + request.getSectionId()));
            if (!Objects.equals(newSection.getName(), oldSectionName)) {
                String currentName = station.getName();
                logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'Участок' изменено с '" + oldSectionName + "' на '" + newSection.getName() + "'",
                        "Участок", oldSectionName, newSection.getName(), author);
                station.setSection(newSection);
            }
        }

        station = stationRepository.save(station);
        return toDTO(station);
    }

    // ==================== DELETE ====================

    @Transactional
    public void delete(String uid) {
        Station station = stationRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Станция не найдена: " + uid));

        logEvent(uid, "DELETE", "Удаление станции: '" + station.getName() + "'", null, station.getName(), null, userService.getCurrentUsername());

        stationRepository.delete(station);
    }

    // ==================== EVENTS ====================

    public List<StationEventLogDto> getEvents(String stationUid) {
        return eventLogRepository.findByStationUidOrderByCreatedAtDesc(stationUid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<StationEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(e -> !"DOCUMENT_ADD".equals(e.getEventType()) 
                        && !"DOCUMENT_DELETE".equals(e.getEventType())
                        && !"DOCUMENT_RENAME".equals(e.getEventType())
                        && !"STRUCTURE_CREATE".equals(e.getEventType())
                        && !"STRUCTURE_UPDATE".equals(e.getEventType())
                        && !"IMAGE_ADD".equals(e.getEventType())
                        && !"IMAGE_DELETE".equals(e.getEventType())
                        && !"COPY".equals(e.getEventType()))
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
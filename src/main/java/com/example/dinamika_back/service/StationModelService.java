// StationModelService.java — ПОЛНЫЙ ФАЙЛ (логирование изображений и структуры в станции)
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.*;
import com.example.dinamika_back.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationModelService {

    private final StationModelRepository modelRepository;
    private final StationTypeRepository typeRepository;
    private final StationManufacturerRepository manufacturerRepository;
    private final StationModelImageRepository imageRepository;
    private final StationConfigurationRepository configurationRepository;
    private final StationModelDocumentRepository documentRepository;
    private final StationModelEventLogRepository eventLogRepository;
    private final StationModelColumnSettingsService columnSettingsService;
    private final UserService userService;
    private final StationConfigurationEventLogRepository configurationEventLogRepository;
    private final StationRepository stationRepository;
    private final StationEventLogRepository stationEventLogRepository;

    private static final String UPLOAD_DIR = "uploads/station-models/";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of("name", "code", "typeName", "manufacturerName", "article", "description", "revision");
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("name"));

    // ==================== GET ALL WITH SETTINGS ====================

    public StationModelListResponse getAllWithSettings(Integer userId) {
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

        List<StationModelDto> models = modelRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        StationModelListResponse response = new StationModelListResponse();
        response.setColumns(orderedColumns);
        response.setData(models);
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

    public List<StationModelDto> getAll() {
        return modelRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public StationModelDto getById(UUID uid) {
        StationModel model = modelRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Модель станции не найдена: " + uid));
        return toDTO(model);
    }

    public Integer generateCode() {
        Integer maxCode = modelRepository.findMaxCode();
        return maxCode + 1;
    }

    private String generateCellsStructure(CreateStationModelRequest request) {
        if (request.getColumns() != null && request.getCellsPerColumn() != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"type\":\"postamat\",");
            sb.append("\"columns\":").append(request.getColumns()).append(",");
            sb.append("\"cellsPerColumn\":").append(request.getCellsPerColumn()).append(",");
            sb.append("\"cells\":[");
            boolean first = true;
            for (int col = 1; col <= request.getColumns(); col++) {
                for (int row = 1; row <= request.getCellsPerColumn(); row++) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("{");
                    sb.append("\"id\":\"").append(UUID.randomUUID().toString()).append("\",");
                    sb.append("\"column\":").append(col).append(",");
                    sb.append("\"row\":").append(row).append(",");
                    sb.append("\"colSpan\":1,");
                    sb.append("\"rowSpan\":1,");
                    sb.append("\"deleted\":false");
                    sb.append("}");
                }
            }
            sb.append("]");
            sb.append("}");
            return sb.toString();
        }

        if (request.getDrums() != null && request.getColumnsPerDrum() != null && request.getRowsPerColumn() != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"type\":\"drum\",");
            sb.append("\"drums\":").append(request.getDrums()).append(",");
            sb.append("\"columnsPerDrum\":").append(request.getColumnsPerDrum()).append(",");
            sb.append("\"rowsPerColumn\":").append(request.getRowsPerColumn()).append(",");
            sb.append("\"cells\":[");
            boolean first = true;
            for (int drum = 1; drum <= request.getDrums(); drum++) {
                for (int col = 1; col <= request.getColumnsPerDrum(); col++) {
                    for (int row = 1; row <= request.getRowsPerColumn(); row++) {
                        if (!first) sb.append(",");
                        first = false;
                        sb.append("{");
                        sb.append("\"id\":\"").append(UUID.randomUUID().toString()).append("\",");
                        sb.append("\"drum\":").append(drum).append(",");
                        sb.append("\"column\":").append(col).append(",");
                        sb.append("\"row\":").append(row).append(",");
                        sb.append("\"colSpan\":1,");
                        sb.append("\"rowSpan\":1,");
                        sb.append("\"deleted\":false");
                        sb.append("}");
                    }
                }
            }
            sb.append("]");
            sb.append("}");
            return sb.toString();
        }

        return null;
    }

    @Transactional
    public StationModelDto create(CreateStationModelRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Наименование модели обязательно");
        }
        StationModel model = new StationModel();
        model.setUid(request.getUid() != null ? request.getUid() : UUID.randomUUID());
        model.setCode(generateCode());
        model.setName(request.getName());
        model.setArticle(request.getArticle());
        model.setRevision(request.getRevision());
        model.setPurpose(request.getPurpose());

        if (request.getCellsStructure() != null && !request.getCellsStructure().isEmpty()) {
            model.setCellsStructure(request.getCellsStructure());
        } else {
            model.setCellsStructure(generateCellsStructure(request));
        }

        if (request.getTypeId() != null) {
            model.setType(typeRepository.findById(request.getTypeId())
                    .orElseThrow(() -> new RuntimeException("Тип станции не найден: " + request.getTypeId())));
        }
        if (request.getManufacturerId() != null) {
            model.setManufacturer(manufacturerRepository.findById(request.getManufacturerId())
                    .orElseThrow(() -> new RuntimeException("Производитель не найден: " + request.getManufacturerId())));
        }

        model = modelRepository.save(model);
        createDefaultConfiguration(model);

        String author = userService.getCurrentUsername();
        logEvent(model.getUid(), "CREATE", "Создание модели станции: '" + model.getName() + "'", null, null, null, author);
        
        if (model.getCellsStructure() != null && !model.getCellsStructure().isEmpty()) {
            logEvent(model.getUid(), "STRUCTURE_CREATE", "Создана структура ячеек", null, null, null, author);
        }

        return toDTO(model);
    }

    @Transactional
    public StationModelDto update(UUID uid, UpdateStationModelRequest request) {
        StationModel model = modelRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Модель станции не найдена: " + uid));

        String author = userService.getCurrentUsername();

        if (request.getName() != null && !request.getName().isBlank()) {
            if (!model.getName().equals(request.getName())) {
                String oldName = model.getName();
                logEvent(uid, "UPDATE", "'" + oldName + "': Значение поля 'Наименование' изменено с '" + oldName + "' на '" + request.getName() + "'",
                        "Наименование", oldName, request.getName(), author);
                model.setName(request.getName());

                List<StationConfiguration> relatedConfigs = configurationRepository.findByModelUidOrderByNameAsc(uid);
                for (StationConfiguration config : relatedConfigs) {
                    logConfigurationEvent(config.getUid(), "UPDATE",
                            "'" + config.getName() + "': Значение поля 'Модель станции' изменено с '" + oldName + "' на '" + request.getName() + "' через справочник 'Модели станций'",
                            "Модель станции", oldName, request.getName(), author);
                }

                List<Station> relatedStations = stationRepository.findAll().stream()
                        .filter(s -> s.getModel() != null && s.getModel().getUid().equals(uid))
                        .collect(Collectors.toList());
                for (Station station : relatedStations) {
                    logStationEvent(station.getUid(), "UPDATE",
                            "'" + station.getName() + "': Значение поля 'Модель' изменено с '" + oldName + "' на '" + request.getName() + "' через справочник 'Модели станций'",
                            "Модель", oldName, request.getName(), author);
                }
            }
        }
        if (request.getArticle() != null) {
            if (!request.getArticle().equals(model.getArticle())) {
                String currentName = model.getName();
                String oldVal = model.getArticle() != null ? model.getArticle() : "null";
                String newVal = request.getArticle() != null ? request.getArticle() : "null";
                logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'Артикул' изменено с '" + oldVal + "' на '" + newVal + "'",
                        "Артикул", oldVal, newVal, author);
                model.setArticle(request.getArticle());

                List<Station> relatedStations = stationRepository.findAll().stream()
                        .filter(s -> s.getModel() != null && s.getModel().getUid().equals(uid))
                        .collect(Collectors.toList());
                for (Station station : relatedStations) {
                    logStationEvent(station.getUid(), "UPDATE",
                            "'" + station.getName() + "': Значение поля 'Артикул' изменено с '" + oldVal + "' на '" + newVal + "' через справочник 'Модели станций'",
                            "Артикул", oldVal, newVal, author);
                }
            }
        }
        if (request.getRevision() != null) {
            if (!request.getRevision().equals(model.getRevision())) {
                String currentName = model.getName();
                String oldVal = model.getRevision() != null ? model.getRevision() : "null";
                String newVal = request.getRevision() != null ? request.getRevision() : "null";
                logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'Ревизия' изменено с '" + oldVal + "' на '" + newVal + "'",
                        "Ревизия", oldVal, newVal, author);
                model.setRevision(request.getRevision());

                List<Station> relatedStations = stationRepository.findAll().stream()
                        .filter(s -> s.getModel() != null && s.getModel().getUid().equals(uid))
                        .collect(Collectors.toList());
                for (Station station : relatedStations) {
                    logStationEvent(station.getUid(), "UPDATE",
                            "'" + station.getName() + "': Значение поля 'Ревизия' изменено с '" + oldVal + "' на '" + newVal + "' через справочник 'Модели станций'",
                            "Ревизия", oldVal, newVal, author);
                }
            }
        }
        if (request.getPurpose() != null) {
            if (!request.getPurpose().equals(model.getPurpose())) {
                String currentName = model.getName();
                String oldVal = model.getPurpose() != null ? model.getPurpose() : "null";
                String newVal = request.getPurpose() != null ? request.getPurpose() : "null";
                logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'Описание' изменено с '" + oldVal + "' на '" + newVal + "'",
                        "Описание", oldVal, newVal, author);
                model.setPurpose(request.getPurpose());
            }
        }
        if (request.getTypeId() != null) {
            String oldTypeName = model.getType() != null ? model.getType().getName() : null;
            StationType type = typeRepository.findById(request.getTypeId())
                    .orElseThrow(() -> new RuntimeException("Тип станции не найден: " + request.getTypeId()));
            if (oldTypeName == null || !oldTypeName.equals(type.getName())) {
                String currentName = model.getName();
                logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'Тип станции' изменено с '" + oldTypeName + "' на '" + type.getName() + "'",
                        "Тип станции", oldTypeName, type.getName(), author);
                model.setType(type);

                List<Station> relatedStations = stationRepository.findAll().stream()
                        .filter(s -> s.getModel() != null && s.getModel().getUid().equals(uid))
                        .collect(Collectors.toList());
                for (Station station : relatedStations) {
                    logStationEvent(station.getUid(), "UPDATE",
                            "'" + station.getName() + "': Значение поля 'Тип станции' изменено с '" + oldTypeName + "' на '" + type.getName() + "' через справочник 'Модели станций'",
                            "Тип станции", oldTypeName, type.getName(), author);
                }
            }
        }
        if (request.getManufacturerId() != null) {
            String oldManufacturerName = model.getManufacturer() != null ? model.getManufacturer().getName() : null;
            StationManufacturer manufacturer = manufacturerRepository.findById(request.getManufacturerId())
                    .orElseThrow(() -> new RuntimeException("Производитель не найден: " + request.getManufacturerId()));
            if (oldManufacturerName == null || !oldManufacturerName.equals(manufacturer.getName())) {
                String currentName = model.getName();
                logEvent(uid, "UPDATE", "'" + currentName + "': Значение поля 'Производитель' изменено с '" + oldManufacturerName + "' на '" + manufacturer.getName() + "'",
                        "Производитель", oldManufacturerName, manufacturer.getName(), author);
                model.setManufacturer(manufacturer);

                List<Station> relatedStations = stationRepository.findAll().stream()
                        .filter(s -> s.getModel() != null && s.getModel().getUid().equals(uid))
                        .collect(Collectors.toList());
                for (Station station : relatedStations) {
                    logStationEvent(station.getUid(), "UPDATE",
                            "'" + station.getName() + "': Значение поля 'Производитель' изменено с '" + oldManufacturerName + "' на '" + manufacturer.getName() + "' через справочник 'Модели станций'",
                            "Производитель", oldManufacturerName, manufacturer.getName(), author);
                }
            }
        }

        String newCellsStructure = null;
        if (request.getCellsStructure() != null && !request.getCellsStructure().isEmpty()) {
            newCellsStructure = request.getCellsStructure();
        } else if (request.getColumns() != null || request.getDrums() != null) {
            CreateStationModelRequest createReq = new CreateStationModelRequest();
            createReq.setColumns(request.getColumns()); createReq.setCellsPerColumn(request.getCellsPerColumn());
            createReq.setDrums(request.getDrums()); createReq.setColumnsPerDrum(request.getColumnsPerDrum()); createReq.setRowsPerColumn(request.getRowsPerColumn());
            newCellsStructure = generateCellsStructure(createReq);
        }

        if (newCellsStructure != null && !newCellsStructure.equals(model.getCellsStructure())) {
            logEvent(uid, "STRUCTURE_UPDATE", "Обновлена структура ячеек", null, null, null, author);
            
            List<Station> relatedStations = stationRepository.findAll().stream()
                    .filter(s -> s.getModel() != null && s.getModel().getUid().equals(uid))
                    .collect(Collectors.toList());
            for (Station station : relatedStations) {
                logStationEvent(station.getUid(), "STRUCTURE_UPDATE", "Обновлена структура ячеек модели", null, null, null, author);
            }
            
            model.setCellsStructure(newCellsStructure);
        }

        model = modelRepository.save(model);
        return toDTO(model);
    }

    @Transactional
    public void delete(UUID uid) {
        StationModel model = modelRepository.findById(uid).orElseThrow(() -> new RuntimeException("Модель станции не найдена: " + uid));

        String author = userService.getCurrentUsername();

        List<StationConfiguration> relatedConfigs = configurationRepository.findByModelUidOrderByNameAsc(uid);
        for (StationConfiguration config : relatedConfigs) {
            logConfigurationEvent(config.getUid(), "UPDATE",
                    "'" + config.getName() + "': Значение поля 'Модель станции' изменено с '" + model.getName() + "' на 'null' через справочник 'Модели станций'",
                    "Модель станции", model.getName(), null, author);
        }

        List<Station> relatedStations = stationRepository.findAll().stream()
                .filter(s -> s.getModel() != null && s.getModel().getUid().equals(uid))
                .collect(Collectors.toList());
        for (Station station : relatedStations) {
            logStationEvent(station.getUid(), "UPDATE",
                    "'" + station.getName() + "': Значение поля 'Модель' изменено с '" + model.getName() + "' на 'null' через справочник 'Модели станций'",
                    "Модель", model.getName(), null, author);
        }

        deleteAllImages(uid);
        documentRepository.deleteByModelUid(uid);

        logEvent(uid, "DELETE", "Удаление модели станции: '" + model.getName() + "'", null, model.getName(), null, author);

        modelRepository.delete(model);
    }

    private void createDefaultConfiguration(StationModel model) {
        StationConfiguration config = new StationConfiguration();
        config.setUid(UUID.randomUUID());
        config.setName("Типовая конфигурация модели " + model.getName());
        config.setModel(model);
        config.setCellsStructure(model.getCellsStructure());
        configurationRepository.save(config);
    }

    // ==================== EVENTS ====================

    public List<StationModelEventLogDto> getEvents(UUID stationModelUid) {
        return eventLogRepository.findByStationModelUidOrderByCreatedAtDesc(stationModelUid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<StationModelEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(e -> !"STRUCTURE_CREATE".equals(e.getEventType()) && !"STRUCTURE_UPDATE".equals(e.getEventType())
                        && !"IMAGE_ADD".equals(e.getEventType()) && !"IMAGE_DELETE".equals(e.getEventType())
                        && !"DOCUMENT_ADD".equals(e.getEventType()) && !"DOCUMENT_RENAME".equals(e.getEventType())
                        && !"DOCUMENT_DELETE".equals(e.getEventType()))
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE ====================

    private void logEvent(UUID modelUid, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        StationModelEventLog log = StationModelEventLog.builder()
                .uid(UUID.randomUUID())
                .stationModelUid(modelUid)
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

    private void logConfigurationEvent(UUID configUid, String eventType, String description,
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
        configurationEventLogRepository.save(log);
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

    private StationModelEventLogDto toEventDTO(StationModelEventLog e) {
        return StationModelEventLogDto.builder()
                .uid(e.getUid())
                .stationModelUid(e.getStationModelUid())
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

    // ==================== IMAGES ====================

    public List<StationModelImageDto> getImages(UUID modelUid) {
        return imageRepository.findByModelUidOrderBySortOrderAsc(modelUid).stream()
                .map(img -> new StationModelImageDto(img.getUid(), img.getModel().getUid(), img.getFilePath(), img.getOriginalName(), getFileUrl(modelUid, img.getFilePath()), img.getSortOrder()))
                .collect(Collectors.toList());
    }

    @Transactional
    public StationModelImageDto uploadImage(UUID modelUid, MultipartFile file) throws IOException {
        StationModel model = modelRepository.findById(modelUid).orElseThrow(() -> new RuntimeException("Модель станции не найдена: " + modelUid));
        String fileName = saveFile(modelUid, file);
        StationModelImage image = new StationModelImage();
        image.setUid(UUID.randomUUID()); image.setModel(model); image.setFilePath(fileName); image.setOriginalName(file.getOriginalFilename()); image.setSortOrder(0);
        image = imageRepository.save(image);
        
        String author = userService.getCurrentUsername();
        logEvent(modelUid, "IMAGE_ADD", "Добавлено изображение: '" + file.getOriginalFilename() + "'", null, null, null, author);
        
        // Логируем в станции
        List<Station> relatedStations = stationRepository.findAll().stream()
                .filter(s -> s.getModel() != null && s.getModel().getUid().equals(modelUid))
                .collect(Collectors.toList());
        for (Station station : relatedStations) {
            logStationEvent(station.getUid(), "IMAGE_ADD", "Добавлено изображение модели: '" + file.getOriginalFilename() + "'", null, null, null, author);
        }
        
        return new StationModelImageDto(image.getUid(), modelUid, fileName, file.getOriginalFilename(), getFileUrl(modelUid, fileName), 0);
    }

    @Transactional
    public void deleteImage(UUID imageUid) {
        StationModelImage image = imageRepository.findById(imageUid).orElseThrow(() -> new RuntimeException("Изображение не найдено: " + imageUid));
        UUID modelUid = image.getModel().getUid();
        deleteFile(modelUid, image.getFilePath());
        imageRepository.delete(image);
        
        String author = userService.getCurrentUsername();
        logEvent(modelUid, "IMAGE_DELETE", "Удалено изображение: '" + image.getOriginalName() + "'", null, null, null, author);
        
        // Логируем в станции
        List<Station> relatedStations = stationRepository.findAll().stream()
                .filter(s -> s.getModel() != null && s.getModel().getUid().equals(modelUid))
                .collect(Collectors.toList());
        for (Station station : relatedStations) {
            logStationEvent(station.getUid(), "IMAGE_DELETE", "Удалено изображение модели: '" + image.getOriginalName() + "'", null, null, null, author);
        }
    }

    private void deleteAllImages(UUID modelUid) {
        List<StationModelImage> images = imageRepository.findByModelUidOrderBySortOrderAsc(modelUid);
        for (StationModelImage img : images) deleteFile(modelUid, img.getFilePath());
        imageRepository.deleteByModelUid(modelUid);
        try { Files.deleteIfExists(Path.of(UPLOAD_DIR, modelUid.toString())); } catch (IOException ignored) {}
    }

    // ==================== DOCUMENTS ====================

    public List<StationModelDocumentDto> getDocuments(UUID modelUid) {
        return documentRepository.findByModelUidOrderByCreatedAtDesc(modelUid).stream()
                .map(doc -> StationModelDocumentDto.builder()
                        .uid(doc.getUid()).modelUid(doc.getModelUid())
                        .documentName(doc.getDocumentName()).filePath(doc.getFilePath())
                        .originalName(doc.getOriginalName()).url(getDocumentFileUrl(modelUid, doc.getFilePath()))
                        .createdAt(doc.getCreatedAt()).build())
                .collect(Collectors.toList());
    }

    @Transactional
    public StationModelDocumentDto uploadDocument(UUID modelUid, String documentName, MultipartFile file) throws IOException {
        modelRepository.findById(modelUid).orElseThrow(() -> new RuntimeException("Модель станции не найдена: " + modelUid));
        String fileName = saveDocumentFile(modelUid, file);
        StationModelDocument document = StationModelDocument.builder()
                .uid(UUID.randomUUID()).modelUid(modelUid).documentName(documentName)
                .filePath(fileName).originalName(file.getOriginalFilename()).createdAt(LocalDateTime.now()).build();
        documentRepository.save(document);
        
        logEvent(modelUid, "DOCUMENT_ADD", "Добавлен документ: '" + documentName + "'", null, null, null, userService.getCurrentUsername());
        
        return StationModelDocumentDto.builder()
                .uid(document.getUid()).modelUid(modelUid).documentName(document.getDocumentName())
                .filePath(document.getFilePath()).originalName(document.getOriginalName())
                .url(getDocumentFileUrl(modelUid, fileName)).createdAt(document.getCreatedAt()).build();
    }

    @Transactional
    public StationModelDocumentDto renameDocument(UUID documentUid, String newDocumentName) {
        StationModelDocument document = documentRepository.findById(documentUid)
                .orElseThrow(() -> new RuntimeException("Документ не найден: " + documentUid));
        String oldName = document.getDocumentName();
        document.setDocumentName(newDocumentName);
        documentRepository.save(document);
        
        logEvent(document.getModelUid(), "DOCUMENT_RENAME", "Документ переименован с '" + oldName + "' на '" + newDocumentName + "'", null, null, null, userService.getCurrentUsername());
        
        return StationModelDocumentDto.builder()
                .uid(document.getUid()).modelUid(document.getModelUid()).documentName(document.getDocumentName())
                .filePath(document.getFilePath()).originalName(document.getOriginalName())
                .url(getDocumentFileUrl(document.getModelUid(), document.getFilePath())).createdAt(document.getCreatedAt()).build();
    }

    @Transactional
    public void deleteDocument(UUID documentUid) {
        StationModelDocument document = documentRepository.findById(documentUid).orElseThrow(() -> new RuntimeException("Документ не найден: " + documentUid));
        UUID modelUid = document.getModelUid();
        String docName = document.getDocumentName();
        deleteDocumentFile(modelUid, document.getFilePath());
        documentRepository.delete(document);
        
        logEvent(modelUid, "DOCUMENT_DELETE", "Удален документ: '" + docName + "'", null, null, null, userService.getCurrentUsername());
    }

    private String saveDocumentFile(UUID modelUid, MultipartFile file) throws IOException {
        Path dir = Path.of(UPLOAD_DIR, modelUid.toString(), "documents");
        if (!Files.exists(dir)) Files.createDirectories(dir);
        String fileName = UUID.randomUUID() + getFileExtension(file.getOriginalFilename());
        Files.copy(file.getInputStream(), dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        return fileName;
    }

    private void deleteDocumentFile(UUID modelUid, String fileName) {
        try { Files.deleteIfExists(Path.of(UPLOAD_DIR, modelUid.toString(), "documents", fileName)); } catch (IOException ignored) {}
    }

    private String getDocumentFileUrl(UUID modelUid, String filePath) {
        return "/uploads/station-models/" + modelUid + "/documents/" + filePath;
    }

    // ==================== FILE HELPERS ====================

    private String saveFile(UUID modelUid, MultipartFile file) throws IOException {
        Path dir = Path.of(UPLOAD_DIR, modelUid.toString());
        if (!Files.exists(dir)) Files.createDirectories(dir);
        String fileName = UUID.randomUUID() + getFileExtension(file.getOriginalFilename());
        Files.copy(file.getInputStream(), dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        return fileName;
    }

    private void deleteFile(UUID modelUid, String fileName) {
        try { Files.deleteIfExists(Path.of(UPLOAD_DIR, modelUid.toString(), fileName)); } catch (IOException ignored) {}
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }

    private String getFileUrl(UUID modelUid, String filePath) {
        return "/uploads/station-models/" + modelUid + "/" + filePath;
    }

    private StationModelDto toDTO(StationModel model) {
        return StationModelDto.builder()
                .uid(model.getUid())
                .code(model.getCode())
                .name(model.getName())
                .article(model.getArticle())
                .revision(model.getRevision())
                .typeId(model.getType() != null ? model.getType().getUid() : null)
                .typeName(model.getType() != null ? model.getType().getName() : null)
                .manufacturerId(model.getManufacturer() != null ? model.getManufacturer().getUid() : null)
                .manufacturerName(model.getManufacturer() != null ? model.getManufacturer().getName() : null)
                .purpose(model.getPurpose())
                .cellsStructure(model.getCellsStructure())
                .build();
    }
}
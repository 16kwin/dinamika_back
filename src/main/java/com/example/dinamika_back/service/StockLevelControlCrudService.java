package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.*;
import com.example.dinamika_back.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockLevelControlCrudService {

    private final DocStockLevelControlRepository docRepository;
    private final RegStockLevelControlBindingRepository bindingRepository;
    private final RegStockLevelControlRepository regRepository;
    private final StockLevelControlEventLogRepository eventLogRepository;
    private final StockLevelControlColumnSettingsService columnSettingsService;
    private final StationRepository stationRepository;
    private final SprMaterialRepository materialRepository;
    private final UserService userService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> ALL_COLUMNS_ORDER = List.of(
            "code", "docDate", "stationName", "isPosted", "bindingsCount", "uid"
    );

    // ==================== GET ALL ====================

    public StockLevelControlListResponse getAll(Integer userId) {
        String columnsJson = columnSettingsService.getColumnsJson(userId);
        Set<String> visibleColumns = new LinkedHashSet<>();
        Map<String, Double> columnWidths = new HashMap<>();
        Set<String> requiredColumns = new LinkedHashSet<>();

        if (columnsJson != null && !columnsJson.isEmpty() && !columnsJson.equals("{}")) {
            parseColumnSettings(columnsJson, visibleColumns, columnWidths, requiredColumns);
        } else {
            visibleColumns = new LinkedHashSet<>(ALL_COLUMNS_ORDER);
        }

        List<String> orderedColumns = ALL_COLUMNS_ORDER.stream()
                .filter(visibleColumns::contains)
                .collect(Collectors.toList());

        List<DocStockLevelControl> docs = docRepository.findAllWithRelations();

        List<Map<String, Object>> data = docs.stream()
                .map(this::buildFullRowData)
                .collect(Collectors.toList());

        StockLevelControlListResponse response = new StockLevelControlListResponse();
        response.setColumns(orderedColumns);
        response.setData(data);
        response.setColumnWidths(columnWidths);
        response.setRequiredColumns(new ArrayList<>(requiredColumns));
        return response;
    }

    private void parseColumnSettings(String json, Set<String> visibleColumns,
                                     Map<String, Double> columnWidths, Set<String> requiredColumns) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof Boolean) {
                    if ((Boolean) value) visibleColumns.add(key);
                } else if (value instanceof Map) {
                    Map<String, Object> settings = (Map<String, Object>) value;
                    Object visible = settings.get("visible");
                    Object width = settings.get("width");
                    Object required = settings.get("required");
                    if (visible instanceof Boolean && (Boolean) visible) visibleColumns.add(key);
                    if (width instanceof Number) columnWidths.put(key, ((Number) width).doubleValue());
                    if (required instanceof Boolean && (Boolean) required) requiredColumns.add(key);
                }
            }
        } catch (Exception e) {
            visibleColumns.addAll(ALL_COLUMNS_ORDER);
        }
    }

    // ==================== GET BY UID ====================

    public StockLevelControlDto getByUid(UUID uid) {
        DocStockLevelControl doc = docRepository.findByUidWithRelations(uid)
                .orElseThrow(() -> new RuntimeException("Документ не найден: " + uid));
        return toDTO(doc);
    }

    // ==================== GET REG BY STATION + MATERIAL ====================

    public StockLevelControlRegDto getRegByStationAndMaterial(String stationUid, UUID materialUid) {
        if (stationUid == null || materialUid == null) return null;
        return regRepository.findByStationUidAndMaterialUid(stationUid, materialUid)
                .map(reg -> StockLevelControlRegDto.builder()
                        .uid(reg.getUid())
                        .stationUid(reg.getStation() != null ? reg.getStation().getUid() : null)
                        .materialUid(reg.getMaterial() != null ? reg.getMaterial().getUid() : null)
                        .minStock(reg.getMinStock())
                        .criticalStock(reg.getCriticalStock())
                        .build())
                .orElse(null);
    }

    // ==================== GENERATE CODE ====================

    public Integer generateCode() {
        Integer max = docRepository.findMaxCode();
        return max != null ? max + 1 : 1;
    }

    // ==================== CREATE ====================

    @Transactional
    public StockLevelControlDto create(CreateStockLevelControlRequest request) {
        UUID uid = request.getUid() != null ? request.getUid() : UUID.randomUUID();

        if (docRepository.existsById(uid)) {
            throw new RuntimeException("Документ с таким uid уже существует: " + uid);
        }

        DocStockLevelControl doc = DocStockLevelControl.builder()
                .uid(uid)
                .code(generateCode())
                .docDate(request.getDocDate() != null ? request.getDocDate() : LocalDate.now())
                .isPosted(false)
                .build();

        if (request.getStationUid() != null) {
            Station station = stationRepository.findByUid(request.getStationUid())
                    .orElseThrow(() -> new RuntimeException("Станция не найдена: " + request.getStationUid()));
            doc.setStation(station);
        }

        DocStockLevelControl savedDoc = docRepository.save(doc);
        final LocalDate docDate = savedDoc.getDocDate();

        if (request.getBindings() != null) {
            for (CreateStockLevelControlRequest.BindingRequest b : request.getBindings()) {
                RegStockLevelControlBinding binding = RegStockLevelControlBinding.builder()
                        .doc(savedDoc)
                        .bindingDate(b.getBindingDate() != null ? b.getBindingDate() : docDate)
                        .minStock(b.getMinStock())
                        .criticalStock(b.getCriticalStock())
                        .build();
                if (b.getMaterialUid() != null) {
                    SprMaterial material = materialRepository.findById(b.getMaterialUid())
                            .orElseThrow(() -> new RuntimeException("Номенклатура не найдена: " + b.getMaterialUid()));
                    binding.setMaterial(material);
                }
                bindingRepository.save(binding);
            }
        }

        logEvent(uid, "CREATE", "Создание документа: код " + savedDoc.getCode(),
                null, null, null, userService.getCurrentUsername());

        return toDTO(savedDoc);
    }

    // ==================== UPDATE ====================

    @Transactional
    public StockLevelControlDto update(UUID uid, UpdateStockLevelControlRequest request) {
        DocStockLevelControl doc = docRepository.findByUidWithRelations(uid)
                .orElseThrow(() -> new RuntimeException("Документ не найден: " + uid));

        if (Boolean.TRUE.equals(doc.getIsPosted())) {
            throw new RuntimeException("Проведённый документ нельзя редактировать");
        }

        String author = userService.getCurrentUsername();

        if (request.getDocDate() != null && !Objects.equals(request.getDocDate(), doc.getDocDate())) {
            String oldVal = doc.getDocDate() != null ? doc.getDocDate().toString() : "null";
            String newVal = request.getDocDate().toString();
            logEvent(uid, "UPDATE", "Значение поля 'Дата' изменено с '" + oldVal + "' на '" + newVal + "'",
                    "Дата", oldVal, newVal, author);
            doc.setDocDate(request.getDocDate());
        }

        if (request.getStationUid() != null) {
            String oldStationUid = doc.getStation() != null ? doc.getStation().getUid() : null;
            if (!Objects.equals(request.getStationUid(), oldStationUid)) {
                Station station = stationRepository.findByUid(request.getStationUid())
                        .orElseThrow(() -> new RuntimeException("Станция не найдена: " + request.getStationUid()));
                String oldName = doc.getStation() != null ? doc.getStation().getName() : "null";
                logEvent(uid, "UPDATE", "Значение поля 'Станция' изменено с '" + oldName + "' на '" + station.getName() + "'",
                        "Станция", oldName, station.getName(), author);
                doc.setStation(station);
            }
        }

        DocStockLevelControl savedDoc = docRepository.save(doc);
        final LocalDate docDate = savedDoc.getDocDate();

        if (request.getBindings() != null) {
            bindingRepository.deleteByDocUid(uid);
            bindingRepository.flush();

            for (CreateStockLevelControlRequest.BindingRequest b : request.getBindings()) {
                RegStockLevelControlBinding binding = RegStockLevelControlBinding.builder()
                        .doc(savedDoc)
                        .bindingDate(b.getBindingDate() != null ? b.getBindingDate() : docDate)
                        .minStock(b.getMinStock())
                        .criticalStock(b.getCriticalStock())
                        .build();
                if (b.getMaterialUid() != null) {
                    SprMaterial material = materialRepository.findById(b.getMaterialUid())
                            .orElseThrow(() -> new RuntimeException("Номенклатура не найдена: " + b.getMaterialUid()));
                    binding.setMaterial(material);
                }
                bindingRepository.save(binding);
            }
        }

        return toDTO(savedDoc);
    }

    // ==================== DELETE ====================

    @Transactional
    public void delete(UUID uid) {
        DocStockLevelControl doc = docRepository.findByUidWithRelations(uid)
                .orElseThrow(() -> new RuntimeException("Документ не найден: " + uid));

        if (Boolean.TRUE.equals(doc.getIsPosted())) {
            throw new RuntimeException("Проведённый документ нельзя удалить");
        }

        logEvent(uid, "DELETE", "Удаление документа: код " + doc.getCode(),
                null, String.valueOf(doc.getCode()), null, userService.getCurrentUsername());

        docRepository.delete(doc);
    }

    // ==================== POST ====================

    @Transactional
    public StockLevelControlDto post(UUID uid) {
        DocStockLevelControl doc = docRepository.findByUidWithRelations(uid)
                .orElseThrow(() -> new RuntimeException("Документ не найден: " + uid));

        if (Boolean.TRUE.equals(doc.getIsPosted())) {
            throw new RuntimeException("Документ уже проведён");
        }

        if (doc.getStation() == null) {
            throw new RuntimeException("Не указана станция в документе");
        }

        List<RegStockLevelControlBinding> bindings = bindingRepository.findByDocUid(uid);

        for (RegStockLevelControlBinding b : bindings) {
            if (b.getMaterial() == null) continue;

            RegStockLevelControl reg = regRepository
                    .findByStationUidAndMaterialUid(doc.getStation().getUid(), b.getMaterial().getUid())
                    .orElseGet(() -> RegStockLevelControl.builder()
                            .station(doc.getStation())
                            .material(b.getMaterial())
                            .build());

            reg.setMinStock(b.getMinStock());
            reg.setCriticalStock(b.getCriticalStock());
            regRepository.save(reg);
        }

        doc.setIsPosted(true);
        DocStockLevelControl savedDoc = docRepository.save(doc);

        logEvent(uid, "POST", "Проведение документа: код " + savedDoc.getCode(),
                null, "false", "true", userService.getCurrentUsername());

        return toDTO(savedDoc);
    }

    // ==================== UNPOST ====================

    @Transactional
    public StockLevelControlDto unpost(UUID uid) {
        DocStockLevelControl doc = docRepository.findByUidWithRelations(uid)
                .orElseThrow(() -> new RuntimeException("Документ не найден: " + uid));

        if (!Boolean.TRUE.equals(doc.getIsPosted())) {
            throw new RuntimeException("Документ не проведён");
        }

        doc.setIsPosted(false);
        DocStockLevelControl savedDoc = docRepository.save(doc);

        logEvent(uid, "UNPOST", "Отмена проведения документа: код " + savedDoc.getCode(),
                null, "true", "false", userService.getCurrentUsername());

        return toDTO(savedDoc);
    }

    // ==================== EVENTS ====================

    public List<StockLevelControlEventLogDto> getEvents(UUID uid) {
        return eventLogRepository.findByDocUidOrderByCreatedAtDesc(uid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<StockLevelControlEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE ====================

    private void logEvent(UUID docUid, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        StockLevelControlEventLog log = StockLevelControlEventLog.builder()
                .docUid(docUid)
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

    private StockLevelControlEventLogDto toEventDTO(StockLevelControlEventLog e) {
        return StockLevelControlEventLogDto.builder()
                .uid(e.getUid())
                .docUid(e.getDocUid())
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

    private StockLevelControlDto toDTO(DocStockLevelControl doc) {
        StockLevelControlDto dto = new StockLevelControlDto();
        dto.setUid(doc.getUid());
        dto.setCode(doc.getCode());
        dto.setDocDate(doc.getDocDate());
        dto.setIsPosted(doc.getIsPosted());
        dto.setCreatedAt(doc.getCreatedAt());
        dto.setUpdatedAt(doc.getUpdatedAt());

        if (doc.getStation() != null) {
            dto.setStationUid(doc.getStation().getUid());
            dto.setStationName(doc.getStation().getName());
        }

        List<RegStockLevelControlBinding> bindings = bindingRepository.findByDocUid(doc.getUid());
        dto.setBindings(bindings.stream().map(this::toBindingDTO).collect(Collectors.toList()));

        return dto;
    }

    private StockLevelControlBindingDto toBindingDTO(RegStockLevelControlBinding b) {
        StockLevelControlBindingDto dto = StockLevelControlBindingDto.builder()
                .uid(b.getUid())
                .bindingDate(b.getBindingDate())
                .minStock(b.getMinStock())
                .criticalStock(b.getCriticalStock())
                .build();
        if (b.getMaterial() != null) {
            dto.setMaterialUid(b.getMaterial().getUid());
            dto.setMaterialName(b.getMaterial().getNameMaterial());
            dto.setMaterialArticle(b.getMaterial().getArticle());
            dto.setMaterialCode(b.getMaterial().getCodeMaterial());
        }
        return dto;
    }

    private Map<String, Object> buildFullRowData(DocStockLevelControl doc) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uid", doc.getUid());
        row.put("code", doc.getCode());
        row.put("docDate", doc.getDocDate());
        row.put("isPosted", doc.getIsPosted());
        row.put("stationUid", doc.getStation() != null ? doc.getStation().getUid() : null);
        row.put("stationName", doc.getStation() != null ? doc.getStation().getName() : null);

        List<RegStockLevelControlBinding> bindings = bindingRepository.findByDocUid(doc.getUid());
        row.put("bindingsCount", bindings.size());

        return row;
    }
}
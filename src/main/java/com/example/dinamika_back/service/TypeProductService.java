// TypeProductService.java — ПОЛНЫЙ ФАЙЛ
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.SprMaterial;
import com.example.dinamika_back.model.SprTypeProduct;
import com.example.dinamika_back.model.SprTypePurpose;
import com.example.dinamika_back.model.TypeProductEventLog;
import com.example.dinamika_back.repository.SprMaterialRepository;
import com.example.dinamika_back.repository.SprTypeProductRepository;
import com.example.dinamika_back.repository.SprTypePurposeRepository;
import com.example.dinamika_back.repository.TypeProductEventLogRepository;
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
public class TypeProductService {

    private final SprTypeProductRepository typeProductRepository;
    private final SprTypePurposeRepository typePurposeRepository;
    private final SprMaterialRepository materialRepository;
    private final TypeProductEventLogRepository eventLogRepository;
    private final TypeProductColumnSettingsService columnSettingsService;
    private final UserService userService;
    private final NomenclatureService nomenclatureService;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of("name", "typePurposeName");
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("name", "typePurposeName"));

    // ==================== GET ALL WITH SETTINGS ====================

    public TypeProductListResponse getAllWithSettings(Integer userId) {
        String columnsJson = columnSettingsService.getColumnsJson(userId);
        Set<String> visibleColumns = new LinkedHashSet<>(ALL_COLUMNS_ORDER);
        Map<String, Double> columnWidths = new HashMap<>();
        Set<String> requiredColumns = new LinkedHashSet<>(REQUIRED_COLUMNS);

        if (columnsJson != null && !columnsJson.isEmpty() && !"{}".equals(columnsJson)) {
            parseColumnSettings(columnsJson, visibleColumns, columnWidths, requiredColumns);
        }

        List<String> orderedColumns = ALL_COLUMNS_ORDER.stream()
                .filter(visibleColumns::contains)
                .collect(Collectors.toList());

        List<Map<String, Object>> data = typeProductRepository.findAll().stream()
                .map(p -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uid", p.getUid().toString());
                    row.put("name", p.getTypeName());
                    row.put("typePurposeUid", p.getTypePurpose() != null ? p.getTypePurpose().getUid().toString() : null);
                    row.put("typePurposeName", p.getTypePurpose() != null ? p.getTypePurpose().getTypeName() : null);
                    row.put("typeMaterialName", p.getTypePurpose() != null && p.getTypePurpose().getTypeMaterial() != null
                            ? p.getTypePurpose().getTypeMaterial().getTypeName() : null);
                    return row;
                })
                .collect(Collectors.toList());

        TypeProductListResponse response = new TypeProductListResponse();
        response.setColumns(orderedColumns);
        response.setData(data);
        response.setColumnWidths(columnWidths);
        response.setRequiredColumns(new ArrayList<>(requiredColumns));
        return response;
    }

    private void parseColumnSettings(String json, Set<String> visibleColumns, Map<String, Double> columnWidths, Set<String> requiredColumns) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            
            if (map.isEmpty()) {
                visibleColumns.clear();
                visibleColumns.addAll(ALL_COLUMNS_ORDER);
                return;
            }
            
            visibleColumns.clear();
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
            
            if (visibleColumns.isEmpty()) {
                visibleColumns.addAll(ALL_COLUMNS_ORDER);
            }
        } catch (Exception e) {
            visibleColumns.clear();
            visibleColumns.addAll(ALL_COLUMNS_ORDER);
        }
    }

    // ==================== CRUD ====================

    @Transactional
    public Map<String, Object> create(CreateTypeProductRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Наименование вида номенклатуры обязательно");
        }

        SprTypeProduct product = new SprTypeProduct();
        product.setUid(UUID.randomUUID());
        product.setTypeName(request.getName());
        
        if (request.getTypePurposeUid() != null) {
            SprTypePurpose purpose = typePurposeRepository.findById(request.getTypePurposeUid())
                    .orElseThrow(() -> new RuntimeException("Группа номенклатуры не найдена: " + request.getTypePurposeUid()));
            product.setTypePurpose(purpose);
        }
        
        product = typeProductRepository.save(product);

        String author = userService.getCurrentUsername();
        logEvent(product.getUid(), "CREATE", "Создание вида номенклатуры: '" + product.getTypeName() + "'",
                null, null, null, author);

        return toRowData(product);
    }

    @Transactional
    public Map<String, Object> update(UUID uid, UpdateTypeProductRequest request) {
        SprTypeProduct product = typeProductRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Вид номенклатуры не найден: " + uid));

        String author = userService.getCurrentUsername();

        if (request.getName() != null && !request.getName().isBlank()) {
            if (!product.getTypeName().equals(request.getName())) {
                String oldName = product.getTypeName();
                logEvent(uid, "UPDATE",
                        "'" + oldName + "': Значение поля 'Наименование' изменено с '" + oldName + "' на '" + request.getName() + "'",
                        "Наименование", oldName, request.getName(), author);
                product.setTypeName(request.getName());

                // Логируем в номенклатуру
                List<SprMaterial> relatedMaterials = materialRepository.findByTypeProductUid(uid);
                for (SprMaterial material : relatedMaterials) {
                    nomenclatureService.logEventFromReference(material.getUid(), "Вид номенклатуры", oldName, request.getName(), author, "справочник 'Виды номенклатуры'");
                }
            }
        }

        if (request.getTypePurposeUid() != null) {
            SprTypePurpose newPurpose = typePurposeRepository.findById(request.getTypePurposeUid())
                    .orElseThrow(() -> new RuntimeException("Группа номенклатуры не найдена: " + request.getTypePurposeUid()));
            String oldPurposeName = product.getTypePurpose() != null ? product.getTypePurpose().getTypeName() : "null";
            if (!newPurpose.getTypeName().equals(oldPurposeName)) {
                logEvent(uid, "UPDATE",
                        "'" + product.getTypeName() + "': Значение поля 'Группа номенклатуры' изменено с '" + oldPurposeName + "' на '" + newPurpose.getTypeName() + "'",
                        "Группа номенклатуры", oldPurposeName, newPurpose.getTypeName(), author);
                product.setTypePurpose(newPurpose);
            }
        } else if (request.getTypePurposeUid() == null && product.getTypePurpose() != null) {
            String oldPurposeName = product.getTypePurpose().getTypeName();
            logEvent(uid, "UPDATE",
                    "'" + product.getTypeName() + "': Значение поля 'Группа номенклатуры' изменено с '" + oldPurposeName + "' на 'null'",
                    "Группа номенклатуры", oldPurposeName, null, author);
            product.setTypePurpose(null);
        }

        product = typeProductRepository.save(product);
        return toRowData(product);
    }

    @Transactional
    public void delete(UUID uid) {
        SprTypeProduct product = typeProductRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Вид номенклатуры не найден: " + uid));

        String author = userService.getCurrentUsername();

        // Логируем в номенклатуру и обнуляем привязку
        List<SprMaterial> relatedMaterials = materialRepository.findByTypeProductUid(uid);
        for (SprMaterial material : relatedMaterials) {
            nomenclatureService.logEventFromReference(material.getUid(), "Вид номенклатуры", product.getTypeName(), null, author, "справочник 'Виды номенклатуры'");
            material.setTypeProduct(null);
            materialRepository.save(material);
        }

        logEvent(uid, "DELETE", "Удаление вида номенклатуры: '" + product.getTypeName() + "'",
                null, product.getTypeName(), null, author);

        typeProductRepository.delete(product);
    }

    // ==================== EVENTS ====================

    public List<TypeProductEventLogDto> getEvents(UUID typeProductUid) {
        return eventLogRepository.findByTypeProductUidOrderByCreatedAtDesc(typeProductUid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<TypeProductEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PUBLIC LOGGING ====================

    @Transactional
    public void logEventFromTypePurpose(UUID typeProductUid, String eventType, String description,
                                         String fieldName, String oldValue, String newValue, String author) {
        TypeProductEventLog log = TypeProductEventLog.builder()
                .uid(UUID.randomUUID())
                .typeProductUid(typeProductUid)
                .eventType(eventType)
                .eventDescription(description)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .author(author)
                .source("Через справочник 'Группы номенклатуры'")
                .createdAt(LocalDateTime.now())
                .build();
        eventLogRepository.save(log);
    }

    // ==================== PRIVATE ====================

    private void logEvent(UUID typeProductUid, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        TypeProductEventLog log = TypeProductEventLog.builder()
                .uid(UUID.randomUUID())
                .typeProductUid(typeProductUid)
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

    private TypeProductEventLogDto toEventDTO(TypeProductEventLog e) {
        return TypeProductEventLogDto.builder()
                .uid(e.getUid())
                .typeProductUid(e.getTypeProductUid())
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

    private Map<String, Object> toRowData(SprTypeProduct p) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uid", p.getUid().toString());
        row.put("name", p.getTypeName());
        row.put("typePurposeUid", p.getTypePurpose() != null ? p.getTypePurpose().getUid().toString() : null);
        row.put("typePurposeName", p.getTypePurpose() != null ? p.getTypePurpose().getTypeName() : null);
        row.put("typeMaterialName", p.getTypePurpose() != null && p.getTypePurpose().getTypeMaterial() != null
                ? p.getTypePurpose().getTypeMaterial().getTypeName() : null);
        return row;
    }
}
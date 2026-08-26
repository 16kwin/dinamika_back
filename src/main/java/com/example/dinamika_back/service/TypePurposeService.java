// TypePurposeService.java — ПОЛНЫЙ ФАЙЛ
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.SprMaterial;
import com.example.dinamika_back.model.SprTypeMaterial;
import com.example.dinamika_back.model.SprTypeProduct;
import com.example.dinamika_back.model.SprTypePurpose;
import com.example.dinamika_back.model.TypeProductEventLog;
import com.example.dinamika_back.model.TypePurposeEventLog;
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
public class TypePurposeService {

    private final SprTypePurposeRepository typePurposeRepository;
    private final SprTypeMaterialRepository typeMaterialRepository;
    private final SprTypeProductRepository typeProductRepository;
    private final SprMaterialRepository materialRepository;
    private final TypePurposeEventLogRepository eventLogRepository;
    private final TypeProductEventLogRepository typeProductEventLogRepository;
    private final TypePurposeColumnSettingsService columnSettingsService;
    private final UserService userService;
    private final NomenclatureService nomenclatureService;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of("name", "typeMaterialName");
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("name", "typeMaterialName"));

    // ==================== GET ALL WITH SETTINGS ====================

    public TypePurposeListResponse getAllWithSettings(Integer userId) {
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

        List<Map<String, Object>> data = typePurposeRepository.findAll().stream()
                .map(p -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uid", p.getUid().toString());
                    row.put("name", p.getTypeName());
                    row.put("typeMaterialUid", p.getTypeMaterial() != null ? p.getTypeMaterial().getUid().toString() : null);
                    row.put("typeMaterialName", p.getTypeMaterial() != null ? p.getTypeMaterial().getTypeName() : null);
                    return row;
                })
                .collect(Collectors.toList());

        TypePurposeListResponse response = new TypePurposeListResponse();
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
    public Map<String, Object> create(CreateTypePurposeRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Наименование группы номенклатуры обязательно");
        }

        SprTypePurpose purpose = new SprTypePurpose();
        purpose.setUid(UUID.randomUUID());
        purpose.setTypeName(request.getName());
        
        if (request.getTypeMaterialUid() != null) {
            SprTypeMaterial typeMaterial = typeMaterialRepository.findById(request.getTypeMaterialUid())
                    .orElseThrow(() -> new RuntimeException("Группа учета не найдена: " + request.getTypeMaterialUid()));
            purpose.setTypeMaterial(typeMaterial);
        }
        
        purpose = typePurposeRepository.save(purpose);

        String author = userService.getCurrentUsername();
        logEvent(purpose.getUid(), "CREATE", "Создание группы номенклатуры: '" + purpose.getTypeName() + "'",
                null, null, null, author);

        return toRowData(purpose);
    }

    @Transactional
    public Map<String, Object> update(UUID uid, UpdateTypePurposeRequest request) {
        SprTypePurpose purpose = typePurposeRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Группа номенклатуры не найдена: " + uid));

        String author = userService.getCurrentUsername();

        if (request.getName() != null && !request.getName().isBlank()) {
            if (!purpose.getTypeName().equals(request.getName())) {
                String oldName = purpose.getTypeName();
                logEvent(uid, "UPDATE",
                        "'" + oldName + "': Значение поля 'Наименование' изменено с '" + oldName + "' на '" + request.getName() + "'",
                        "Наименование", oldName, request.getName(), author);
                purpose.setTypeName(request.getName());

                // Логируем в связанные виды номенклатуры
                List<SprTypeProduct> relatedProducts = typeProductRepository.findByTypePurposeUid(uid);
                for (SprTypeProduct product : relatedProducts) {
                    logTypeProductEvent(product.getUid(), "UPDATE",
                            "'" + product.getTypeName() + "': Значение поля 'Группа номенклатуры' изменено с '" + oldName + "' на '" + request.getName() + "' через справочник 'Группы номенклатуры'",
                            "Группа номенклатуры", oldName, request.getName(), author);
                }

                // Логируем в номенклатуру
                List<SprMaterial> relatedMaterials = materialRepository.findByTypePurposeUid(uid);
                for (SprMaterial material : relatedMaterials) {
                    nomenclatureService.logEventFromReference(material.getUid(), "Группа номенклатуры", oldName, request.getName(), author, "справочник 'Группы номенклатуры'");
                }
            }
        }

        if (request.getTypeMaterialUid() != null) {
            SprTypeMaterial newTypeMaterial = typeMaterialRepository.findById(request.getTypeMaterialUid())
                    .orElseThrow(() -> new RuntimeException("Группа учета не найдена: " + request.getTypeMaterialUid()));
            String oldTypeMaterialName = purpose.getTypeMaterial() != null ? purpose.getTypeMaterial().getTypeName() : "null";
            if (!newTypeMaterial.getTypeName().equals(oldTypeMaterialName)) {
                logEvent(uid, "UPDATE",
                        "'" + purpose.getTypeName() + "': Значение поля 'Группа учета' изменено с '" + oldTypeMaterialName + "' на '" + newTypeMaterial.getTypeName() + "'",
                        "Группа учета", oldTypeMaterialName, newTypeMaterial.getTypeName(), author);
                purpose.setTypeMaterial(newTypeMaterial);

                // Логируем в связанные виды номенклатуры
                List<SprTypeProduct> relatedProducts = typeProductRepository.findByTypePurposeUid(uid);
                for (SprTypeProduct product : relatedProducts) {
                    logTypeProductEvent(product.getUid(), "UPDATE",
                            "'" + product.getTypeName() + "': Значение поля 'Группа учета' изменено с '" + oldTypeMaterialName + "' на '" + newTypeMaterial.getTypeName() + "' через справочник 'Группы номенклатуры'",
                            "Группа учета", oldTypeMaterialName, newTypeMaterial.getTypeName(), author);
                }
            }
        }

        purpose = typePurposeRepository.save(purpose);
        return toRowData(purpose);
    }

    @Transactional
    public void delete(UUID uid) {
        SprTypePurpose purpose = typePurposeRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Группа номенклатуры не найдена: " + uid));

        String author = userService.getCurrentUsername();

        // Логируем в связанные виды номенклатуры и обнуляем привязку
        List<SprTypeProduct> relatedProducts = typeProductRepository.findByTypePurposeUid(uid);
        for (SprTypeProduct product : relatedProducts) {
            logTypeProductEvent(product.getUid(), "UPDATE",
                    "'" + product.getTypeName() + "': Значение поля 'Группа номенклатуры' изменено с '" + purpose.getTypeName() + "' на 'null' через справочник 'Группы номенклатуры'",
                    "Группа номенклатуры", purpose.getTypeName(), null, author);
            product.setTypePurpose(null);
            typeProductRepository.save(product);
        }

        // Логируем в номенклатуру и обнуляем привязку
        List<SprMaterial> relatedMaterials = materialRepository.findByTypePurposeUid(uid);
        for (SprMaterial material : relatedMaterials) {
            nomenclatureService.logEventFromReference(material.getUid(), "Группа номенклатуры", purpose.getTypeName(), null, author, "справочник 'Группы номенклатуры'");
            material.setTypePurpose(null);
            materialRepository.save(material);
        }

        logEvent(uid, "DELETE", "Удаление группы номенклатуры: '" + purpose.getTypeName() + "'",
                null, purpose.getTypeName(), null, author);

        typePurposeRepository.delete(purpose);
    }

    // ==================== EVENTS ====================

    public List<TypePurposeEventLogDto> getEvents(UUID typePurposeUid) {
        return eventLogRepository.findByTypePurposeUidOrderByCreatedAtDesc(typePurposeUid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<TypePurposeEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PUBLIC LOGGING ====================

    @Transactional
    public void logEventFromTypeMaterial(UUID typePurposeUid, String eventType, String description,
                                          String fieldName, String oldValue, String newValue, String author) {
        TypePurposeEventLog log = TypePurposeEventLog.builder()
                .uid(UUID.randomUUID())
                .typePurposeUid(typePurposeUid)
                .eventType(eventType)
                .eventDescription(description)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .author(author)
                .source("Через справочник 'Группы учета'")
                .createdAt(LocalDateTime.now())
                .build();
        eventLogRepository.save(log);
    }

    // ==================== PRIVATE ====================

    private void logEvent(UUID typePurposeUid, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        TypePurposeEventLog log = TypePurposeEventLog.builder()
                .uid(UUID.randomUUID())
                .typePurposeUid(typePurposeUid)
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

    private void logTypeProductEvent(UUID typeProductUid, String eventType, String description,
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
        typeProductEventLogRepository.save(log);
    }

    private TypePurposeEventLogDto toEventDTO(TypePurposeEventLog e) {
        return TypePurposeEventLogDto.builder()
                .uid(e.getUid())
                .typePurposeUid(e.getTypePurposeUid())
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

    private Map<String, Object> toRowData(SprTypePurpose p) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uid", p.getUid().toString());
        row.put("name", p.getTypeName());
        row.put("typeMaterialUid", p.getTypeMaterial() != null ? p.getTypeMaterial().getUid().toString() : null);
        row.put("typeMaterialName", p.getTypeMaterial() != null ? p.getTypeMaterial().getTypeName() : null);
        return row;
    }
}
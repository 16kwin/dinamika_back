// TypeMaterialService.java — ПОЛНЫЙ ФАЙЛ
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.SprMaterial;
import com.example.dinamika_back.model.SprTypeMaterial;
import com.example.dinamika_back.model.SprTypePurpose;
import com.example.dinamika_back.model.TypeMaterialEventLog;
import com.example.dinamika_back.model.TypePurposeEventLog;
import com.example.dinamika_back.repository.SprMaterialRepository;
import com.example.dinamika_back.repository.SprTypeMaterialRepository;
import com.example.dinamika_back.repository.SprTypePurposeRepository;
import com.example.dinamika_back.repository.TypeMaterialEventLogRepository;
import com.example.dinamika_back.repository.TypePurposeEventLogRepository;
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
public class TypeMaterialService {

    private final SprTypeMaterialRepository typeMaterialRepository;
    private final SprTypePurposeRepository typePurposeRepository;
    private final SprMaterialRepository materialRepository;
    private final TypeMaterialEventLogRepository eventLogRepository;
    private final TypePurposeEventLogRepository typePurposeEventLogRepository;
    private final TypeMaterialColumnSettingsService columnSettingsService;
    private final UserService userService;
    private final NomenclatureService nomenclatureService;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of("name");
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("name"));

    // ==================== GET ALL WITH SETTINGS ====================

    public TypeMaterialListResponse getAllWithSettings(Integer userId) {
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

        List<Map<String, Object>> data = typeMaterialRepository.findAll().stream()
                .map(m -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uid", m.getUid().toString());
                    row.put("name", m.getTypeName());
                    return row;
                })
                .collect(Collectors.toList());

        TypeMaterialListResponse response = new TypeMaterialListResponse();
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
    public SprTypeMaterialDTO create(CreateTypeMaterialRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Наименование группы учета обязательно");
        }

        SprTypeMaterial typeMaterial = new SprTypeMaterial();
        typeMaterial.setUid(UUID.randomUUID());
        typeMaterial.setTypeName(request.getName());
        typeMaterial = typeMaterialRepository.save(typeMaterial);

        String author = userService.getCurrentUsername();
        logEvent(typeMaterial.getUid(), "CREATE", "Создание группы учета: '" + typeMaterial.getTypeName() + "'",
                null, null, null, author);

        SprTypeMaterialDTO dto = new SprTypeMaterialDTO();
        dto.setUid(typeMaterial.getUid());
        dto.setTypeName(typeMaterial.getTypeName());
        return dto;
    }

    @Transactional
    public SprTypeMaterialDTO update(UUID uid, UpdateTypeMaterialRequest request) {
        SprTypeMaterial typeMaterial = typeMaterialRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Группа учета не найдена: " + uid));

        String author = userService.getCurrentUsername();

        if (request.getName() != null && !request.getName().isBlank()) {
            if (!typeMaterial.getTypeName().equals(request.getName())) {
                String oldName = typeMaterial.getTypeName();
                logEvent(uid, "UPDATE",
                        "'" + oldName + "': Значение поля 'Наименование' изменено с '" + oldName + "' на '" + request.getName() + "'",
                        "Наименование", oldName, request.getName(), author);
                typeMaterial.setTypeName(request.getName());

                // Логируем в связанные группы номенклатуры
                List<SprTypePurpose> relatedPurposes = typePurposeRepository.findByTypeMaterialUid(uid);
                for (SprTypePurpose purpose : relatedPurposes) {
                    logTypePurposeEvent(purpose.getUid(), "UPDATE",
                            "'" + purpose.getTypeName() + "': Значение поля 'Группа учета' изменено с '" + oldName + "' на '" + request.getName() + "' через справочник 'Группы учета'",
                            "Группа учета", oldName, request.getName(), author);
                }

                // Логируем в номенклатуру
                List<SprMaterial> relatedMaterials = materialRepository.findByTypeMainUid(uid);
                for (SprMaterial material : relatedMaterials) {
                    nomenclatureService.logEventFromReference(material.getUid(), "Группа учета", oldName, request.getName(), author, "справочник 'Группы учета'");
                }
            }
        }

        typeMaterial = typeMaterialRepository.save(typeMaterial);

        SprTypeMaterialDTO dto = new SprTypeMaterialDTO();
        dto.setUid(typeMaterial.getUid());
        dto.setTypeName(typeMaterial.getTypeName());
        return dto;
    }

    @Transactional
    public void delete(UUID uid) {
        SprTypeMaterial typeMaterial = typeMaterialRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Группа учета не найдена: " + uid));

        String author = userService.getCurrentUsername();

        // Логируем в связанные группы номенклатуры и обнуляем привязку
        List<SprTypePurpose> relatedPurposes = typePurposeRepository.findByTypeMaterialUid(uid);
        for (SprTypePurpose purpose : relatedPurposes) {
            logTypePurposeEvent(purpose.getUid(), "UPDATE",
                    "'" + purpose.getTypeName() + "': Значение поля 'Группа учета' изменено с '" + typeMaterial.getTypeName() + "' на 'null' через справочник 'Группы учета'",
                    "Группа учета", typeMaterial.getTypeName(), null, author);
            purpose.setTypeMaterial(null);
            typePurposeRepository.save(purpose);
        }

        // Логируем в номенклатуру и обнуляем привязку
        List<SprMaterial> relatedMaterials = materialRepository.findByTypeMainUid(uid);
        for (SprMaterial material : relatedMaterials) {
            nomenclatureService.logEventFromReference(material.getUid(), "Группа учета", typeMaterial.getTypeName(), null, author, "справочник 'Группы учета'");
            material.setTypeMain(null);
            materialRepository.save(material);
        }

        logEvent(uid, "DELETE", "Удаление группы учета: '" + typeMaterial.getTypeName() + "'",
                null, typeMaterial.getTypeName(), null, author);

        typeMaterialRepository.delete(typeMaterial);
    }

    // ==================== EVENTS ====================

    public List<TypeMaterialEventLogDto> getEvents(UUID typeMaterialUid) {
        return eventLogRepository.findByTypeMaterialUidOrderByCreatedAtDesc(typeMaterialUid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<TypeMaterialEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE ====================

    private void logEvent(UUID typeMaterialUid, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        TypeMaterialEventLog log = TypeMaterialEventLog.builder()
                .uid(UUID.randomUUID())
                .typeMaterialUid(typeMaterialUid)
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

    private void logTypePurposeEvent(UUID typePurposeUid, String eventType, String description,
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
        typePurposeEventLogRepository.save(log);
    }

    private TypeMaterialEventLogDto toEventDTO(TypeMaterialEventLog e) {
        return TypeMaterialEventLogDto.builder()
                .uid(e.getUid())
                .typeMaterialUid(e.getTypeMaterialUid())
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
}
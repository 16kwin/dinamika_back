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
public class SupplierBrandService {

    private final SprSupplierBrandRepository supplierBrandRepository;
    private final SprSupplierRepository supplierRepository;
    private final SupplierBrandEventLogRepository eventLogRepository;
    private final SupplierBrandColumnSettingsService columnSettingsService;
    private final UserService userService;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of("name", "supplierName");
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("name", "supplierName"));

    // ==================== GET ALL WITH SETTINGS ====================

    public SupplierBrandListResponse getAllWithSettings(Integer userId) {
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

        List<Map<String, Object>> data = supplierBrandRepository.findAll().stream()
                .map(b -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uid", b.getUid().toString());
                    row.put("name", b.getName());
                    row.put("supplierUid", b.getSupplier() != null ? b.getSupplier().getUid().toString() : null);
                    row.put("supplierName", b.getSupplier() != null ? b.getSupplier().getName() : null);
                    return row;
                })
                .collect(Collectors.toList());

        SupplierBrandListResponse response = new SupplierBrandListResponse();
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
    public Map<String, Object> create(CreateSupplierBrandRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Наименование бренда поставщика обязательно");
        }

        SprSupplierBrand brand = new SprSupplierBrand();
        brand.setUid(UUID.randomUUID());
        brand.setName(request.getName());

        if (request.getSupplierUid() != null) {
            brand.setSupplier(supplierRepository.findById(request.getSupplierUid())
                    .orElseThrow(() -> new RuntimeException("Поставщик не найден: " + request.getSupplierUid())));
        }

        brand = supplierBrandRepository.save(brand);

        String author = userService.getCurrentUsername();
        logEvent(brand.getUid(), "CREATE", "Создание бренда поставщика: '" + brand.getName() + "'",
                null, null, null, author);

        return toRowData(brand);
    }

    @Transactional
    public Map<String, Object> update(UUID uid, UpdateSupplierBrandRequest request) {
        SprSupplierBrand brand = supplierBrandRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Бренд поставщика не найден: " + uid));

        String author = userService.getCurrentUsername();

        if (request.getName() != null && !request.getName().isBlank()) {
            if (!brand.getName().equals(request.getName())) {
                String oldName = brand.getName();
                logEvent(uid, "UPDATE",
                        "'" + oldName + "': Значение поля 'Наименование' изменено с '" + oldName + "' на '" + request.getName() + "'",
                        "Наименование", oldName, request.getName(), author);
                brand.setName(request.getName());
            }
        }

        if (request.getSupplierUid() != null) {
            SprSupplier newSupplier = supplierRepository.findById(request.getSupplierUid())
                    .orElseThrow(() -> new RuntimeException("Поставщик не найден: " + request.getSupplierUid()));
            String oldSupplierName = brand.getSupplier() != null ? brand.getSupplier().getName() : "null";
            if (!newSupplier.getName().equals(oldSupplierName)) {
                logEvent(uid, "UPDATE",
                        "'" + brand.getName() + "': Значение поля 'Поставщик' изменено с '" + oldSupplierName + "' на '" + newSupplier.getName() + "'",
                        "Поставщик", oldSupplierName, newSupplier.getName(), author);
                brand.setSupplier(newSupplier);
            }
        }

        brand = supplierBrandRepository.save(brand);
        return toRowData(brand);
    }

    @Transactional
    public void delete(UUID uid) {
        SprSupplierBrand brand = supplierBrandRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Бренд поставщика не найден: " + uid));

        String author = userService.getCurrentUsername();

        logEvent(uid, "DELETE", "Удаление бренда поставщика: '" + brand.getName() + "'",
                null, brand.getName(), null, author);

        supplierBrandRepository.delete(brand);
    }

    // ==================== EVENTS ====================

    public List<SupplierBrandEventLogDto> getEvents(UUID brandUid) {
        return eventLogRepository.findBySupplierBrandUidOrderByCreatedAtDesc(brandUid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<SupplierBrandEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE ====================

    private void logEvent(UUID brandUid, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        SupplierBrandEventLog log = SupplierBrandEventLog.builder()
                .uid(UUID.randomUUID())
                .supplierBrandUid(brandUid)
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

    private SupplierBrandEventLogDto toEventDTO(SupplierBrandEventLog e) {
        return SupplierBrandEventLogDto.builder()
                .uid(e.getUid())
                .supplierBrandUid(e.getSupplierBrandUid())
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

    private Map<String, Object> toRowData(SprSupplierBrand b) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uid", b.getUid().toString());
        row.put("name", b.getName());
        row.put("supplierUid", b.getSupplier() != null ? b.getSupplier().getUid().toString() : null);
        row.put("supplierName", b.getSupplier() != null ? b.getSupplier().getName() : null);
        return row;
    }
}
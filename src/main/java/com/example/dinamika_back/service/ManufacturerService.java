// ManufacturerService.java — ПОЛНЫЙ ФАЙЛ
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
public class ManufacturerService {

    private final SprManufacturerRepository manufacturerRepository;
    private final SprCountryRepository countryRepository;
    private final SprProductionDirectionRepository directionRepository;
    private final SprBrandRepository brandRepository;
    private final SprModelOfBrandRepository modelOfBrandRepository;
    private final SprMaterialRepository materialRepository;
    private final ManufacturerEventLogRepository eventLogRepository;
    private final BrandEventLogRepository brandEventLogRepository;
    private final ModelEventLogRepository modelEventLogRepository;
    private final ManufacturerColumnSettingsService columnSettingsService;
    private final UserService userService;
    private final NomenclatureService nomenclatureService;
    private final SprManufacturerImageRepository imageRepository;
    private final SprManufacturerDocumentRepository documentRepository;

    private static final String MANUFACTURER_UPLOAD_DIR = "uploads/manufacturers/";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> ALL_COLUMNS_ORDER = List.of(
            "code", "name", "countryName", "directionName", "address", "description", "email", "website", "phone"
    );
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of(
            "code", "name", "countryName", "directionName"
    ));

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ ФАЙЛОВ ====================

    private Path getManufacturerDir(UUID manufacturerUid) throws IOException {
        Path dir = Path.of(MANUFACTURER_UPLOAD_DIR, manufacturerUid.toString());
        if (!Files.exists(dir)) Files.createDirectories(dir);
        return dir;
    }

    private String saveFile(UUID manufacturerUid, MultipartFile file) throws IOException {
        Path dir = getManufacturerDir(manufacturerUid);
        String extension = getFileExtension(file.getOriginalFilename());
        String fileName = UUID.randomUUID().toString() + extension;
        Path filePath = dir.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        return fileName;
    }

    private void deleteFile(UUID manufacturerUid, String fileName) {
        try {
            Path filePath = Path.of(MANUFACTURER_UPLOAD_DIR, manufacturerUid.toString(), fileName);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }

    private String getFileUrl(UUID manufacturerUid, String filePath) {
        return "/uploads/manufacturers/" + manufacturerUid + "/" + filePath;
    }

    // ==================== ИЗОБРАЖЕНИЯ ====================

    public List<ManufacturerMediaDTO> getImages(UUID manufacturerUid) {
        return imageRepository.findByManufacturerUidOrderBySortOrderAsc(manufacturerUid).stream()
                .map(img -> new ManufacturerMediaDTO(img.getUid(), img.getManufacturer().getUid(),
                        img.getFilePath(), img.getOriginalName(),
                        getFileUrl(manufacturerUid, img.getFilePath()), img.getSortOrder()))
                .collect(Collectors.toList());
    }

    @Transactional
    public ManufacturerMediaDTO uploadImage(UUID manufacturerUid, MultipartFile file) throws IOException {
        String fileName = saveFile(manufacturerUid, file);
        SprManufacturer manufacturer = manufacturerRepository.findById(manufacturerUid)
                .orElseThrow(() -> new RuntimeException("Производитель не найден: " + manufacturerUid));
        long count = imageRepository.findByManufacturerUidOrderBySortOrderAsc(manufacturerUid).size();
        int nextSortOrder = (int) count;
        SprManufacturerImage image = new SprManufacturerImage();
        image.setUid(UUID.randomUUID());
        image.setManufacturer(manufacturer);
        image.setFilePath(fileName);
        image.setOriginalName(file.getOriginalFilename());
        image.setSortOrder(nextSortOrder);
        image.setCreatedAt(LocalDateTime.now());
        imageRepository.save(image);
        logEvent(manufacturerUid, "ADD", "Добавлено изображение '" + file.getOriginalFilename() + "'",
                "Изображение", null, file.getOriginalFilename(), "Система");
        return new ManufacturerMediaDTO(image.getUid(), manufacturerUid, fileName, file.getOriginalFilename(),
                getFileUrl(manufacturerUid, fileName), nextSortOrder);
    }

    @Transactional
    public void deleteImage(UUID uid) {
        SprManufacturerImage image = imageRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Изображение не найдено: " + uid));
        UUID manufacturerUid = image.getManufacturer().getUid();
        String fileName = image.getOriginalName();
        deleteFile(manufacturerUid, image.getFilePath());
        imageRepository.delete(image);
        logEvent(manufacturerUid, "DELETE", "Удалено изображение '" + fileName + "'",
                "Изображение", fileName, null, "Система");
    }

    // ==================== ДОКУМЕНТЫ ====================

    public List<ManufacturerDocumentDTO> getDocuments(UUID manufacturerUid) {
        return documentRepository.findByManufacturerUidOrderByCreatedAtDesc(manufacturerUid).stream()
                .map(doc -> new ManufacturerDocumentDTO(doc.getUid(), doc.getManufacturer().getUid(),
                        doc.getDocumentName(), doc.getFilePath(), doc.getOriginalName(),
                        getFileUrl(manufacturerUid, doc.getFilePath()), doc.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Transactional
    public ManufacturerDocumentDTO uploadDocument(UUID manufacturerUid, String documentName, MultipartFile file) throws IOException {
        String fileName = saveFile(manufacturerUid, file);
        SprManufacturer manufacturer = manufacturerRepository.findById(manufacturerUid)
                .orElseThrow(() -> new RuntimeException("Производитель не найден: " + manufacturerUid));
        SprManufacturerDocument document = new SprManufacturerDocument();
        document.setUid(UUID.randomUUID());
        document.setManufacturer(manufacturer);
        document.setDocumentName(documentName);
        document.setFilePath(fileName);
        document.setOriginalName(file.getOriginalFilename());
        document.setCreatedAt(LocalDateTime.now());
        documentRepository.save(document);
        logEvent(manufacturerUid, "ADD", "Добавлен документ '" + documentName + "'",
                "Документ", null, documentName, "Система");
        return new ManufacturerDocumentDTO(document.getUid(), manufacturerUid, document.getDocumentName(),
                document.getFilePath(), document.getOriginalName(),
                getFileUrl(manufacturerUid, fileName), document.getCreatedAt());
    }

    @Transactional
    public void deleteDocument(UUID uid) {
        SprManufacturerDocument document = documentRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Документ не найден: " + uid));
        UUID manufacturerUid = document.getManufacturer().getUid();
        String docName = document.getDocumentName();
        deleteFile(manufacturerUid, document.getFilePath());
        documentRepository.delete(document);
        logEvent(manufacturerUid, "DELETE", "Удален документ '" + docName + "'",
                "Документ", docName, null, "Система");
    }

    // ==================== Удаление всех медиа ====================

    @Transactional
    public void deleteAllManufacturerMedia(UUID manufacturerUid) {
        imageRepository.deleteByManufacturerUid(manufacturerUid);
        documentRepository.deleteByManufacturerUid(manufacturerUid);
        try {
            Path dir = Path.of(MANUFACTURER_UPLOAD_DIR, manufacturerUid.toString());
            if (Files.exists(dir)) {
                try (var files = Files.list(dir)) {
                    files.forEach(f -> { try { Files.deleteIfExists(f); } catch (IOException ignored) {} });
                }
                Files.deleteIfExists(dir);
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    // ==================== GET ALL WITH SETTINGS ====================

    public ManufacturerListResponse getAllWithSettings(Integer userId) {
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

        List<Map<String, Object>> data = manufacturerRepository.findAll().stream()
                .map(m -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uid", m.getUid().toString());
                    row.put("code", m.getCode());
                    row.put("name", m.getName());
                    row.put("countryUid", m.getCountry() != null ? m.getCountry().getUid().toString() : null);
                    row.put("countryName", m.getCountry() != null ? m.getCountry().getName() : null);
                    row.put("directionUid", m.getDirection() != null ? m.getDirection().getUid().toString() : null);
                    row.put("directionName", m.getDirection() != null ? m.getDirection().getName() : null);
                    row.put("address", m.getAddress());
                    row.put("description", m.getDescription());
                    row.put("email", m.getEmail());
                    row.put("website", m.getWebsite());
                    row.put("phone", m.getPhone());
                    return row;
                })
                .collect(Collectors.toList());

        ManufacturerListResponse response = new ManufacturerListResponse();
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
            if (visibleColumns.isEmpty()) visibleColumns.addAll(ALL_COLUMNS_ORDER);
        } catch (Exception e) {
            visibleColumns.clear();
            visibleColumns.addAll(ALL_COLUMNS_ORDER);
        }
    }

    // ==================== GENERATE CODE ====================

    public Integer generateCode() {
        Integer maxCode = manufacturerRepository.findMaxCode();
        return maxCode != null ? maxCode + 1 : 1;
    }

    // ==================== GET MANUFACTURER ====================

    public Map<String, Object> getManufacturerData(UUID uid) {
        SprManufacturer manufacturer = manufacturerRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Производитель не найден: " + uid));
        return toRowData(manufacturer);
    }

    // ==================== CRUD ====================

    @Transactional
    public Map<String, Object> create(CreateManufacturerRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Наименование производителя обязательно");
        }

        SprManufacturer manufacturer = manufacturerRepository.findById(request.getUid())
                .orElseGet(() -> {
                    SprManufacturer newManufacturer = new SprManufacturer();
                    newManufacturer.setUid(request.getUid());
                    newManufacturer.setCode(generateCode());
                    return newManufacturer;
                });

        manufacturer.setName(request.getName());
        manufacturer.setDescription(request.getDescription());
        manufacturer.setAddress(request.getAddress());
        manufacturer.setEmail(request.getEmail());
        manufacturer.setWebsite(request.getWebsite());
        manufacturer.setPhone(request.getPhone());

        if (request.getCountryUid() != null) {
            manufacturer.setCountry(countryRepository.findById(request.getCountryUid())
                    .orElseThrow(() -> new RuntimeException("Страна не найдена: " + request.getCountryUid())));
        }

        if (request.getDirectionUid() != null) {
            manufacturer.setDirection(directionRepository.findById(request.getDirectionUid())
                    .orElseThrow(() -> new RuntimeException("Направление производства не найдено: " + request.getDirectionUid())));
        }

        boolean isNew = manufacturer.getCode() == null;
        manufacturer = manufacturerRepository.save(manufacturer);

        String author = userService.getCurrentUsername();
        if (isNew) {
            logEvent(manufacturer.getUid(), "CREATE", "Создание производителя: '" + manufacturer.getName() + "'",
                    null, null, null, author);
        }

        return toRowData(manufacturer);
    }

    @Transactional
    public Map<String, Object> update(UUID uid, UpdateManufacturerRequest request) {
        SprManufacturer manufacturer = manufacturerRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Производитель не найден: " + uid));

        String author = userService.getCurrentUsername();

        if (request.getName() != null && !request.getName().isBlank()) {
            if (!manufacturer.getName().equals(request.getName())) {
                String oldName = manufacturer.getName();
                logEvent(uid, "UPDATE",
                        "'" + oldName + "': Значение поля 'Наименование' изменено с '" + oldName + "' на '" + request.getName() + "'",
                        "Наименование", oldName, request.getName(), author);
                manufacturer.setName(request.getName());

                List<SprBrand> relatedBrands = brandRepository.findByManufacturerUid(uid);
                for (SprBrand brand : relatedBrands) {
                    logBrandEvent(brand.getUid(), "UPDATE",
                            "'" + brand.getName() + "': Значение поля 'Производитель' изменено с '" + oldName + "' на '" + request.getName() + "' через справочник 'Производители'",
                            "Производитель", oldName, request.getName(), author);
                }

                List<SprModelOfBrand> relatedModels = modelOfBrandRepository.findByManufacturerUid(uid);
                for (SprModelOfBrand model : relatedModels) {
                    logModelEvent(model.getUid(), "UPDATE",
                            "'" + model.getName() + "': Значение поля 'Производитель' изменено с '" + oldName + "' на '" + request.getName() + "' через справочник 'Производители'",
                            "Производитель", oldName, request.getName(), author);
                }

                // Логируем в номенклатуру
                List<SprMaterial> relatedMaterials = materialRepository.findByManufacturerUid(uid);
                for (SprMaterial material : relatedMaterials) {
                    nomenclatureService.logEventFromReference(material.getUid(), "Производитель", oldName, request.getName(), author, "справочник 'Производители'");
                }
            }
        }

        if (request.getDescription() != null && !Objects.equals(request.getDescription(), manufacturer.getDescription())) {
            String oldVal = manufacturer.getDescription() != null ? manufacturer.getDescription() : "null";
            String newVal = request.getDescription() != null ? request.getDescription() : "null";
            logEvent(uid, "UPDATE",
                    "'" + manufacturer.getName() + "': Значение поля 'Описание' изменено с '" + oldVal + "' на '" + newVal + "'",
                    "Описание", oldVal, newVal, author);
            manufacturer.setDescription(request.getDescription());
        }

        if (request.getAddress() != null && !Objects.equals(request.getAddress(), manufacturer.getAddress())) {
            String oldVal = manufacturer.getAddress() != null ? manufacturer.getAddress() : "null";
            String newVal = request.getAddress() != null ? request.getAddress() : "null";
            logEvent(uid, "UPDATE",
                    "'" + manufacturer.getName() + "': Значение поля 'Адрес' изменено с '" + oldVal + "' на '" + newVal + "'",
                    "Адрес", oldVal, newVal, author);
            manufacturer.setAddress(request.getAddress());
        }

        if (request.getEmail() != null && !Objects.equals(request.getEmail(), manufacturer.getEmail())) {
            String oldVal = manufacturer.getEmail() != null ? manufacturer.getEmail() : "null";
            String newVal = request.getEmail() != null ? request.getEmail() : "null";
            logEvent(uid, "UPDATE",
                    "'" + manufacturer.getName() + "': Значение поля 'E-mail' изменено с '" + oldVal + "' на '" + newVal + "'",
                    "E-mail", oldVal, newVal, author);
            manufacturer.setEmail(request.getEmail());
        }

        if (request.getWebsite() != null && !Objects.equals(request.getWebsite(), manufacturer.getWebsite())) {
            String oldVal = manufacturer.getWebsite() != null ? manufacturer.getWebsite() : "null";
            String newVal = request.getWebsite() != null ? request.getWebsite() : "null";
            logEvent(uid, "UPDATE",
                    "'" + manufacturer.getName() + "': Значение поля 'Сайт' изменено с '" + oldVal + "' на '" + newVal + "'",
                    "Сайт", oldVal, newVal, author);
            manufacturer.setWebsite(request.getWebsite());
        }

        if (request.getPhone() != null && !Objects.equals(request.getPhone(), manufacturer.getPhone())) {
            String oldVal = manufacturer.getPhone() != null ? manufacturer.getPhone() : "null";
            String newVal = request.getPhone() != null ? request.getPhone() : "null";
            logEvent(uid, "UPDATE",
                    "'" + manufacturer.getName() + "': Значение поля 'Телефон' изменено с '" + oldVal + "' на '" + newVal + "'",
                    "Телефон", oldVal, newVal, author);
            manufacturer.setPhone(request.getPhone());
        }

        if (request.getCountryUid() != null) {
            SprCountry newCountry = countryRepository.findById(request.getCountryUid())
                    .orElseThrow(() -> new RuntimeException("Страна не найдена: " + request.getCountryUid()));
            String oldCountryName = manufacturer.getCountry() != null ? manufacturer.getCountry().getName() : "null";
            if (!newCountry.getName().equals(oldCountryName)) {
                logEvent(uid, "UPDATE",
                        "'" + manufacturer.getName() + "': Значение поля 'Страна' изменено с '" + oldCountryName + "' на '" + newCountry.getName() + "'",
                        "Страна", oldCountryName, newCountry.getName(), author);
                manufacturer.setCountry(newCountry);
            }
        }

        if (request.getDirectionUid() != null) {
            SprProductionDirection newDirection = directionRepository.findById(request.getDirectionUid())
                    .orElseThrow(() -> new RuntimeException("Направление производства не найдено: " + request.getDirectionUid()));
            String oldDirectionName = manufacturer.getDirection() != null ? manufacturer.getDirection().getName() : "null";
            if (!newDirection.getName().equals(oldDirectionName)) {
                logEvent(uid, "UPDATE",
                        "'" + manufacturer.getName() + "': Значение поля 'Направление производства' изменено с '" + oldDirectionName + "' на '" + newDirection.getName() + "'",
                        "Направление производства", oldDirectionName, newDirection.getName(), author);
                manufacturer.setDirection(newDirection);
            }
        }

        manufacturer = manufacturerRepository.save(manufacturer);
        return toRowData(manufacturer);
    }

    @Transactional
    public void delete(UUID uid) {
        SprManufacturer manufacturer = manufacturerRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Производитель не найден: " + uid));

        String author = userService.getCurrentUsername();

        List<SprBrand> relatedBrands = brandRepository.findByManufacturerUid(uid);
        for (SprBrand brand : relatedBrands) {
            logBrandEvent(brand.getUid(), "UPDATE",
                    "'" + brand.getName() + "': Значение поля 'Производитель' изменено с '" + manufacturer.getName() + "' на 'null' через справочник 'Производители'",
                    "Производитель", manufacturer.getName(), null, author);
            brand.setManufacturer(null);
            brandRepository.save(brand);
        }

        List<SprModelOfBrand> relatedModels = modelOfBrandRepository.findByManufacturerUid(uid);
        for (SprModelOfBrand model : relatedModels) {
            logModelEvent(model.getUid(), "UPDATE",
                    "'" + model.getName() + "': Значение поля 'Производитель' изменено с '" + manufacturer.getName() + "' на 'null' через справочник 'Производители'",
                    "Производитель", manufacturer.getName(), null, author);
            model.setManufacturer(null);
            modelOfBrandRepository.save(model);
        }

        // Логируем в номенклатуру и обнуляем привязку
        List<SprMaterial> relatedMaterials = materialRepository.findByManufacturerUid(uid);
        for (SprMaterial material : relatedMaterials) {
            nomenclatureService.logEventFromReference(material.getUid(), "Производитель", manufacturer.getName(), null, author, "справочник 'Производители'");
            material.setManufacturer(null);
            materialRepository.save(material);
        }

        logEvent(uid, "DELETE", "Удаление производителя: '" + manufacturer.getName() + "'",
                null, manufacturer.getName(), null, author);

        deleteAllManufacturerMedia(uid);
        manufacturerRepository.delete(manufacturer);
    }

    // ==================== EVENTS ====================

    public List<ManufacturerEventLogDto> getEvents(UUID manufacturerUid) {
        return eventLogRepository.findByManufacturerUidOrderByCreatedAtDesc(manufacturerUid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<ManufacturerEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE ====================

    private void logEvent(UUID manufacturerUid, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        ManufacturerEventLog log = ManufacturerEventLog.builder()
                .uid(UUID.randomUUID())
                .manufacturerUid(manufacturerUid)
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

    private void logBrandEvent(UUID brandUid, String eventType, String description,
                               String fieldName, String oldValue, String newValue, String author) {
        BrandEventLog log = BrandEventLog.builder()
                .uid(UUID.randomUUID())
                .brandUid(brandUid)
                .eventType(eventType)
                .eventDescription(description)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .author(author)
                .source("Через справочник 'Производители'")
                .createdAt(LocalDateTime.now())
                .build();
        brandEventLogRepository.save(log);
    }

    private void logModelEvent(UUID modelUid, String eventType, String description,
                               String fieldName, String oldValue, String newValue, String author) {
        ModelEventLog log = ModelEventLog.builder()
                .uid(UUID.randomUUID())
                .modelUid(modelUid)
                .eventType(eventType)
                .eventDescription(description)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .author(author)
                .source("Через справочник 'Производители'")
                .createdAt(LocalDateTime.now())
                .build();
        modelEventLogRepository.save(log);
    }

    private ManufacturerEventLogDto toEventDTO(ManufacturerEventLog e) {
        return ManufacturerEventLogDto.builder()
                .uid(e.getUid())
                .manufacturerUid(e.getManufacturerUid())
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

    private Map<String, Object> toRowData(SprManufacturer m) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uid", m.getUid().toString());
        row.put("code", m.getCode());
        row.put("name", m.getName());
        row.put("countryUid", m.getCountry() != null ? m.getCountry().getUid().toString() : null);
        row.put("countryName", m.getCountry() != null ? m.getCountry().getName() : null);
        row.put("directionUid", m.getDirection() != null ? m.getDirection().getUid().toString() : null);
        row.put("directionName", m.getDirection() != null ? m.getDirection().getName() : null);
        row.put("address", m.getAddress());
        row.put("description", m.getDescription());
        row.put("email", m.getEmail());
        row.put("website", m.getWebsite());
        row.put("phone", m.getPhone());
        return row;
    }
}
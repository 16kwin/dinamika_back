// SupplierService.java — ПОЛНЫЙ ФАЙЛ (добавлен renameDocument)
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.*;
import com.example.dinamika_back.repository.*;
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
public class SupplierService {

    private final SprSupplierRepository supplierRepository;
    private final SprCountryRepository countryRepository;
    private final SprSupplierDescriptionTypeRepository descriptionTypeRepository;
    private final SprSupplierImageRepository imageRepository;
    private final SprSupplierDocumentRepository documentRepository;
    private final RegSupplierRatingRepository ratingRepository;
    private final RegSupplierIntegrationRepository integrationRepository;
    private final RegSupplierEventLogRepository eventLogRepository;
    private final RegSuppliersRepository regSuppliersRepository;
    private final SprMaterialRepository materialRepository;
    private final SprSupplierBrandRepository supplierBrandRepository;
    private final SupplierBrandEventLogRepository supplierBrandEventLogRepository;

    private static final String SUPPLIER_UPLOAD_DIR = "uploads/suppliers/";

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ ФАЙЛОВ ====================

    private Path getSupplierDir(UUID supplierUid) throws IOException {
        Path dir = Path.of(SUPPLIER_UPLOAD_DIR, supplierUid.toString());
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        return dir;
    }

    private String saveFile(UUID supplierUid, MultipartFile file) throws IOException {
        Path dir = getSupplierDir(supplierUid);
        String extension = getFileExtension(file.getOriginalFilename());
        String fileName = UUID.randomUUID().toString() + extension;
        Path filePath = dir.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        return fileName;
    }

    private void deleteFile(UUID supplierUid, String fileName) {
        try {
            Path filePath = Path.of(SUPPLIER_UPLOAD_DIR, supplierUid.toString(), fileName);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }

    private String getFileUrl(UUID supplierUid, String filePath) {
        return "/uploads/suppliers/" + supplierUid + "/" + filePath;
    }

    // ==================== Генерация кода ====================

    public SupplierCreateResponse generateCode() {
        Integer maxCode = supplierRepository.findMaxCode();
        Integer code = maxCode != null ? maxCode + 1 : 1;
        return new SupplierCreateResponse(UUID.randomUUID(), code);
    }

    // ==================== Получение поставщика ====================

    public SprSupplierDTO getSupplier(UUID uid) {
        SprSupplier supplier = supplierRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Поставщик не найден: " + uid));
        return toDTO(supplier);
    }

    // ==================== Получение всех поставщиков ====================

    public List<SprSupplierDTO> getAllSuppliers() {
        return supplierRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ==================== ЛОГИРОВАНИЕ СОБЫТИЙ ====================

    @Transactional
    public void logEvent(UUID supplierUid, String eventType, String description,
                         String fieldName, String oldValue, String newValue, String author) {
        SprSupplier supplier = supplierRepository.findById(supplierUid).orElse(null);
        if (supplier == null) return;

        RegSupplierEventLog log = RegSupplierEventLog.builder()
                .uid(UUID.randomUUID())
                .supplier(supplier)
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

    public List<SupplierEventLogDTO> getEvents(UUID supplierUid) {
        return eventLogRepository.findBySupplierUidOrderByCreatedAtDesc(supplierUid).stream()
                .map(e -> SupplierEventLogDTO.builder()
                        .uid(e.getUid())
                        .supplierUid(e.getSupplier().getUid())
                        .eventType(e.getEventType())
                        .eventDescription(e.getEventDescription())
                        .fieldName(e.getFieldName())
                        .oldValue(e.getOldValue())
                        .newValue(e.getNewValue())
                        .author(e.getAuthor())
                        .source(e.getSource())
                        .createdAt(e.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private void logFieldChange(UUID supplierUid, String fieldName, String oldValue, String newValue, String author) {
        if (oldValue == null && newValue == null) return;
        if (oldValue != null && oldValue.equals(newValue)) return;
        if (oldValue == null && newValue != null) {
            logEvent(supplierUid, "UPDATE", "Значение поля '" + fieldName + "' установлено: " + newValue,
                    fieldName, null, newValue, author);
        } else if (newValue == null && oldValue != null) {
            logEvent(supplierUid, "UPDATE", "Значение поля '" + fieldName + "' очищено",
                    fieldName, oldValue, null, author);
        } else {
            logEvent(supplierUid, "UPDATE", "Значение поля '" + fieldName + "' изменено с '" + oldValue + "' на '" + newValue + "'",
                    fieldName, oldValue, newValue, author);
        }
    }

    // ==================== Сохранение ====================

    @Transactional
    public void saveDraft(SupplierSaveRequest request) {
        SprSupplier supplier = supplierRepository.findById(request.getUid())
                .orElseGet(() -> {
                    SprSupplier newSupplier = new SprSupplier();
                    newSupplier.setUid(request.getUid());
                    if (request.getCode() == null) {
                        Integer maxCode = supplierRepository.findMaxCode();
                        newSupplier.setCode(maxCode != null ? maxCode + 1 : 1);
                    } else {
                        newSupplier.setCode(request.getCode());
                    }
                    return newSupplier;
                });

        if (supplier.getCode() == null) {
            Integer maxCode = supplierRepository.findMaxCode();
            supplier.setCode(maxCode != null ? maxCode + 1 : 1);
        }

        boolean isNewSupplier = supplier.getName() == null;
        String author = request.getAuthor() != null ? request.getAuthor() : "Система";

        if (!isNewSupplier) {
            logFieldChange(supplier.getUid(), "Наименование", supplier.getName(), request.getName(), author);
            logFieldChange(supplier.getUid(), "Адрес", supplier.getAddress(), request.getAddress(), author);
            logFieldChange(supplier.getUid(), "Описание", supplier.getDescription(), request.getDescription(), author);
            logFieldChange(supplier.getUid(), "Email", supplier.getEmail(), request.getEmail(), author);
            logFieldChange(supplier.getUid(), "Сайт", supplier.getWebsite(), request.getWebsite(), author);
            logFieldChange(supplier.getUid(), "Телефон", supplier.getPhone(), request.getPhone(), author);
            logFieldChange(supplier.getUid(), "ИНН", supplier.getInn(), request.getInn(), author);
            logFieldChange(supplier.getUid(), "ОГРН", supplier.getOgrn(), request.getOgrn(), author);
            logFieldChange(supplier.getUid(), "КПП", supplier.getKpp(), request.getKpp(), author);
            logFieldChange(supplier.getUid(), "Контактное лицо", supplier.getContactPerson(), request.getContactPerson(), author);
            logFieldChange(supplier.getUid(), "Должность контактного лица", supplier.getContactPosition(), request.getContactPosition(), author);
            logFieldChange(supplier.getUid(), "Телефон контактного лица", supplier.getContactPhone(), request.getContactPhone(), author);
            logFieldChange(supplier.getUid(), "Руководитель", supplier.getDirector(), request.getDirector(), author);
            logFieldChange(supplier.getUid(), "Должность руководителя", supplier.getDirectorPosition(), request.getDirectorPosition(), author);
            logFieldChange(supplier.getUid(), "Банк", supplier.getBankName(), request.getBankName(), author);
            logFieldChange(supplier.getUid(), "БИК", supplier.getBik(), request.getBik(), author);
            logFieldChange(supplier.getUid(), "Корр. счет", supplier.getCorrespondentAccount(), request.getCorrespondentAccount(), author);
            logFieldChange(supplier.getUid(), "Расч. счет", supplier.getSettlementAccount(), request.getSettlementAccount(), author);
        }

        supplier.setName(request.getName());
        supplier.setAddress(request.getAddress());
        supplier.setDescription(request.getDescription());
        supplier.setEmail(request.getEmail());
        supplier.setWebsite(request.getWebsite());
        supplier.setPhone(request.getPhone());
        supplier.setInn(request.getInn());
        supplier.setOgrn(request.getOgrn());
        supplier.setKpp(request.getKpp());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setContactPosition(request.getContactPosition());
        supplier.setContactPhone(request.getContactPhone());
        supplier.setDirector(request.getDirector());
        supplier.setDirectorPosition(request.getDirectorPosition());
        supplier.setBankName(request.getBankName());
        supplier.setBik(request.getBik());
        supplier.setCorrespondentAccount(request.getCorrespondentAccount());
        supplier.setSettlementAccount(request.getSettlementAccount());

        if (request.getCountryUid() != null) {
            supplier.setCountry(countryRepository.findById(request.getCountryUid()).orElse(null));
        }
        if (request.getShortDescriptionUid() != null) {
            supplier.setShortDescription(descriptionTypeRepository.findById(request.getShortDescriptionUid()).orElse(null));
        }

        supplierRepository.save(supplier);

        if (isNewSupplier) {
            logEvent(supplier.getUid(), "CREATE", "Создание карточки поставщика", null, null, null, author);
        }
    }

    // ==================== Удаление ====================

    @Transactional
    public void deleteSupplier(UUID uid) {
        SprSupplier supplier = supplierRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Поставщик не найден: " + uid));

        String author = "Система";

        List<SprSupplierBrand> relatedBrands = supplierBrandRepository.findBySupplierUid(uid);
        for (SprSupplierBrand brand : relatedBrands) {
            logSupplierBrandEvent(brand.getUid(), "UPDATE",
                    "'" + brand.getName() + "': Значение поля 'Поставщик' изменено с '" + supplier.getName() + "' на 'null' через справочник 'Поставщики'",
                    "Поставщик", supplier.getName(), null, author);
            brand.setSupplier(null);
            supplierBrandRepository.save(brand);
        }

        logEvent(uid, "DELETE", "Удаление поставщика: '" + supplier.getName() + "'",
                null, supplier.getName(), null, author);

        deleteAllSupplierMedia(uid);
        supplierRepository.deleteById(uid);
    }

    // ==================== ИЗОБРАЖЕНИЯ ====================

    public List<SupplierMediaDTO> getImages(UUID supplierUid) {
        return imageRepository.findBySupplierUidOrderBySortOrderAsc(supplierUid).stream()
                .map(img -> new SupplierMediaDTO(
                        img.getUid(),
                        img.getSupplier().getUid(),
                        img.getFilePath(),
                        img.getOriginalName(),
                        getFileUrl(supplierUid, img.getFilePath()),
                        img.getSortOrder()))
                .collect(Collectors.toList());
    }

    @Transactional
    public SupplierMediaDTO uploadImage(UUID supplierUid, MultipartFile file) throws IOException {
        String fileName = saveFile(supplierUid, file);
        SprSupplier supplier = supplierRepository.findById(supplierUid)
                .orElseThrow(() -> new RuntimeException("Поставщик не найден: " + supplierUid));
        
        List<SprSupplierImage> images = imageRepository.findBySupplierUidOrderBySortOrderAsc(supplierUid);
        int nextSortOrder = images.isEmpty() 
            ? 0 
            : images.stream().mapToInt(SprSupplierImage::getSortOrder).max().orElse(0) + 1;
        
        SprSupplierImage image = new SprSupplierImage();
        image.setUid(UUID.randomUUID());
        image.setSupplier(supplier);
        image.setFilePath(fileName);
        image.setOriginalName(file.getOriginalFilename());
        image.setSortOrder(nextSortOrder);
        imageRepository.save(image);
        logEvent(supplierUid, "ADD", "Добавлено изображение '" + file.getOriginalFilename() + "'",
                "Изображение", null, file.getOriginalFilename(), "Система");
        return new SupplierMediaDTO(image.getUid(), supplierUid, fileName, file.getOriginalFilename(),
                getFileUrl(supplierUid, fileName), nextSortOrder);
    }

    @Transactional
    public void deleteImage(UUID uid) {
        SprSupplierImage image = imageRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Изображение не найдено: " + uid));
        UUID supplierUid = image.getSupplier().getUid();
        String fileName = image.getOriginalName();
        deleteFile(supplierUid, image.getFilePath());
        imageRepository.delete(image);
        logEvent(supplierUid, "DELETE", "Удалено изображение '" + fileName + "'",
                "Изображение", fileName, null, "Система");
    }

    // ==================== ДОКУМЕНТЫ ====================

    public List<SupplierDocumentDTO> getDocuments(UUID supplierUid) {
        return documentRepository.findBySupplierUidOrderByCreatedAtDesc(supplierUid).stream()
                .map(doc -> new SupplierDocumentDTO(doc.getUid(), doc.getSupplier().getUid(),
                        doc.getDocumentName(), doc.getFilePath(), doc.getOriginalName(),
                        getFileUrl(supplierUid, doc.getFilePath()), doc.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Transactional
    public SupplierDocumentDTO uploadDocument(UUID supplierUid, String documentName, MultipartFile file) throws IOException {
        String fileName = saveFile(supplierUid, file);
        SprSupplier supplier = supplierRepository.findById(supplierUid)
                .orElseThrow(() -> new RuntimeException("Поставщик не найден: " + supplierUid));
        SprSupplierDocument document = new SprSupplierDocument();
        document.setUid(UUID.randomUUID());
        document.setSupplier(supplier);
        document.setDocumentName(documentName);
        document.setFilePath(fileName);
        document.setOriginalName(file.getOriginalFilename());
        documentRepository.save(document);
        logEvent(supplierUid, "ADD", "Добавлен документ '" + documentName + "'",
                "Документ", null, documentName, "Система");
        return new SupplierDocumentDTO(document.getUid(), supplierUid, document.getDocumentName(),
                document.getFilePath(), document.getOriginalName(),
                getFileUrl(supplierUid, fileName), document.getCreatedAt());
    }

    @Transactional
    public SupplierDocumentDTO renameDocument(UUID documentUid, String newDocumentName) {
        SprSupplierDocument document = documentRepository.findById(documentUid)
                .orElseThrow(() -> new RuntimeException("Документ не найден: " + documentUid));
        String oldName = document.getDocumentName();
        document.setDocumentName(newDocumentName);
        documentRepository.save(document);
        
        UUID supplierUid = document.getSupplier().getUid();
        logEvent(supplierUid, "UPDATE", "Документ переименован с '" + oldName + "' на '" + newDocumentName + "'",
                "Документ", oldName, newDocumentName, "Система");
        
        return new SupplierDocumentDTO(document.getUid(), supplierUid, document.getDocumentName(),
                document.getFilePath(), document.getOriginalName(),
                getFileUrl(supplierUid, document.getFilePath()), document.getCreatedAt());
    }

    @Transactional
    public void deleteDocument(UUID uid) {
        SprSupplierDocument document = documentRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Документ не найден: " + uid));
        UUID supplierUid = document.getSupplier().getUid();
        String docName = document.getDocumentName();
        deleteFile(supplierUid, document.getFilePath());
        documentRepository.delete(document);
        logEvent(supplierUid, "DELETE", "Удален документ '" + docName + "'",
                "Документ", docName, null, "Система");
    }

    // ==================== РЕЙТИНГ ====================

    public List<SupplierRatingDTO> getRatings(UUID supplierUid) {
        return ratingRepository.findBySupplierUidOrderByCreatedAtDesc(supplierUid).stream()
                .map(r -> new SupplierRatingDTO(r.getUid(), r.getSupplier().getUid(), r.getRating(),
                        r.getComment(), r.getAuthor(), r.getCreatedAt()))
                .collect(Collectors.toList());
    }

    public Double getAverageRating(UUID supplierUid) {
        return ratingRepository.getAverageRatingBySupplierUid(supplierUid);
    }

    @Transactional
    public SupplierRatingDTO addRating(UUID supplierUid, AddSupplierRatingRequest request) {
        SprSupplier supplier = supplierRepository.findById(supplierUid)
                .orElseThrow(() -> new RuntimeException("Поставщик не найден: " + supplierUid));
        RegSupplierRating rating = new RegSupplierRating();
        rating.setUid(UUID.randomUUID());
        rating.setSupplier(supplier);
        rating.setRating(request.getRating());
        rating.setComment(request.getComment());
        rating.setAuthor(request.getAuthor());
        ratingRepository.save(rating);
        logEvent(supplierUid, "ADD", "Добавлен отзыв от '" + request.getAuthor() + "': " + request.getRating() + " звезд",
                "Рейтинг", null, request.getRating().toString(), request.getAuthor());
        return new SupplierRatingDTO(rating.getUid(), supplierUid, rating.getRating(),
                rating.getComment(), rating.getAuthor(), rating.getCreatedAt());
    }

    @Transactional
    public void deleteRating(UUID ratingUid) {
        RegSupplierRating rating = ratingRepository.findById(ratingUid).orElse(null);
        if (rating != null) {
            logEvent(rating.getSupplier().getUid(), "DELETE", "Удален отзыв от '" + rating.getAuthor() + "'",
                    "Рейтинг", rating.getRating().toString(), null, rating.getAuthor());
        }
        ratingRepository.deleteById(ratingUid);
    }

    // ==================== ИНТЕГРАЦИЯ ====================

    public List<SupplierIntegrationDTO> getIntegrations(UUID supplierUid) {
        return integrationRepository.findBySupplierUidOrderByCreatedAtDesc(supplierUid).stream()
                .map(i -> new SupplierIntegrationDTO(i.getUid(), i.getSupplier().getUid(), i.getEvent(),
                        i.getExchangeType(), i.getDirection(), i.getProtocol(), i.getTargetSystem(), i.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Transactional
    public SupplierIntegrationDTO addIntegration(UUID supplierUid, CreateSupplierIntegrationRequest request) {
        SprSupplier supplier = supplierRepository.findById(supplierUid)
                .orElseThrow(() -> new RuntimeException("Поставщик не найден: " + supplierUid));
        RegSupplierIntegration integration = new RegSupplierIntegration();
        integration.setUid(UUID.randomUUID());
        integration.setSupplier(supplier);
        integration.setEvent("Объект синхронизирован");
        integration.setExchangeType(request.getExchangeType());
        integration.setDirection(request.getDirection());
        integration.setProtocol(request.getProtocol());
        integration.setTargetSystem(request.getTargetSystem());
        integrationRepository.save(integration);
        return new SupplierIntegrationDTO(integration.getUid(), supplierUid, integration.getEvent(),
                integration.getExchangeType(), integration.getDirection(), integration.getProtocol(),
                integration.getTargetSystem(), integration.getCreatedAt());
    }

    @Transactional
    public void deleteIntegration(UUID integrationUid) {
        integrationRepository.deleteById(integrationUid);
    }

    // ==================== ТИПЫ ОПИСАНИЙ ====================

    public List<SupplierDescriptionTypeDTO> getDescriptionTypes() {
        return descriptionTypeRepository.findAll().stream()
                .map(t -> new SupplierDescriptionTypeDTO(t.getUid(), t.getName()))
                .collect(Collectors.toList());
    }

    // ==================== БРЕНДЫ ПОСТАВЩИКА ====================

    public List<SupplierBrandDTO> getBrands(UUID supplierUid) {
        return supplierBrandRepository.findBySupplierUid(supplierUid).stream()
                .map(brand -> SupplierBrandDTO.builder()
                        .uid(brand.getUid())
                        .name(brand.getName())
                        .supplierUid(brand.getSupplier() != null ? brand.getSupplier().getUid() : null)
                        .supplierName(brand.getSupplier() != null ? brand.getSupplier().getName() : null)
                        .build())
                .collect(Collectors.toList());
    }

    // ==================== ПОСТАВКИ ====================

    public List<MaterialSupplyDTO> getDeliveries(UUID supplierUid) {
        return regSuppliersRepository.findBySupplierUid(supplierUid).stream()
                .map(r -> new MaterialSupplyDTO(
                        r.getUid(),
                        r.getMaterial() != null ? r.getMaterial().getUid() : null,
                        r.getMaterial() != null ? r.getMaterial().getNameMaterial() : null,
                        r.getSupplier() != null ? r.getSupplier().getUid() : null,
                        r.getSupplier() != null ? r.getSupplier().getName() : null,
                        r.getSupplyDate(),
                        r.getDocumentName(),
                        r.getFilePath(),
                        r.getOriginalName(),
                        r.getFilePath() != null ? getFileUrl(supplierUid, r.getFilePath()) : null))
                .collect(Collectors.toList());
    }

    @Transactional
    public MaterialSupplyDTO addDelivery(UUID supplierUid, UUID materialUid, String supplyDate,
                                         String documentName, MultipartFile file) throws IOException {
        SprSupplier supplier = supplierRepository.findById(supplierUid)
                .orElseThrow(() -> new RuntimeException("Поставщик не найден: " + supplierUid));
        SprMaterial material = materialRepository.findById(materialUid)
                .orElseThrow(() -> new RuntimeException("Материал не найден: " + materialUid));

        RegSuppliers regSuppliers = new RegSuppliers();
        regSuppliers.setUid(UUID.randomUUID());
        regSuppliers.setMaterial(material);
        regSuppliers.setSupplier(supplier);
        regSuppliers.setSupplyDate(supplyDate != null ? LocalDateTime.parse(supplyDate) : LocalDateTime.now());
        regSuppliers.setDocumentName(documentName);

        if (file != null && !file.isEmpty()) {
            String fileName = saveFile(supplierUid, file);
            regSuppliers.setFilePath(fileName);
            regSuppliers.setOriginalName(file.getOriginalFilename());
        }

        regSuppliersRepository.save(regSuppliers);

        logEvent(supplierUid, "ADD", "Добавлена поставка материала '" + material.getNameMaterial() + "'",
                "Поставка", null, material.getNameMaterial(), "Система");

        return new MaterialSupplyDTO(
                regSuppliers.getUid(),
                material.getUid(),
                material.getNameMaterial(),
                supplier.getUid(),
                supplier.getName(),
                regSuppliers.getSupplyDate(),
                regSuppliers.getDocumentName(),
                regSuppliers.getFilePath(),
                regSuppliers.getOriginalName(),
                regSuppliers.getFilePath() != null ? getFileUrl(supplierUid, regSuppliers.getFilePath()) : null);
    }

    @Transactional
    public void deleteDelivery(UUID uid) {
        RegSuppliers regSuppliers = regSuppliersRepository.findById(uid).orElse(null);
        if (regSuppliers != null) {
            UUID supplierUid = regSuppliers.getSupplier().getUid();
            String materialName = regSuppliers.getMaterial() != null ? regSuppliers.getMaterial().getNameMaterial() : "";
            if (regSuppliers.getFilePath() != null) {
                deleteFile(supplierUid, regSuppliers.getFilePath());
            }
            regSuppliersRepository.deleteById(uid);
            logEvent(supplierUid, "DELETE", "Удалена поставка материала '" + materialName + "'",
                    "Поставка", materialName, null, "Система");
        } else {
            regSuppliersRepository.deleteById(uid);
        }
    }

    // ==================== АССОРТИМЕНТ ====================

    public List<MaterialItemDTO> getAssortment(UUID supplierUid) {
        List<RegSuppliers> supplies = regSuppliersRepository.findBySupplierUid(supplierUid);
        return supplies.stream()
                .filter(s -> s.getMaterial() != null)
                .map(s -> {
                    SprMaterial m = s.getMaterial();
                    MaterialItemDTO item = new MaterialItemDTO();
                    item.setUid(m.getUid());
                    item.setName(m.getNameMaterial());
                    item.setArticle(m.getArticle());
                    item.setCode(m.getCodeMaterial());
                    item.setTypeMainName(m.getTypeMain() != null ? m.getTypeMain().getTypeName() : null);
                    return item;
                })
                .distinct()
                .collect(Collectors.toList());
    }

    // ==================== Удаление всех медиа ====================

    @Transactional
    public void deleteAllSupplierMedia(UUID supplierUid) {
        imageRepository.deleteBySupplierUid(supplierUid);
        documentRepository.deleteBySupplierUid(supplierUid);
        ratingRepository.deleteBySupplierUid(supplierUid);
        integrationRepository.deleteBySupplierUid(supplierUid);
        try {
            Path dir = Path.of(SUPPLIER_UPLOAD_DIR, supplierUid.toString());
            if (Files.exists(dir)) {
                try (var files = Files.list(dir)) {
                    files.forEach(f -> { try { Files.deleteIfExists(f); } catch (IOException ignored) {} });
                }
                Files.deleteIfExists(dir);
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    // ==================== ЛОГИРОВАНИЕ БРЕНДОВ ПОСТАВЩИКОВ ====================

    private void logSupplierBrandEvent(UUID brandUid, String eventType, String description,
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
                .source("Через справочник 'Поставщики'")
                .createdAt(LocalDateTime.now())
                .build();
        supplierBrandEventLogRepository.save(log);
    }

    // ==================== DTO конвертер ====================

    private SprSupplierDTO toDTO(SprSupplier s) {
        return SprSupplierDTO.builder()
                .uid(s.getUid()).code(s.getCode()).name(s.getName())
                .countryUid(s.getCountry() != null ? s.getCountry().getUid() : null)
                .countryName(s.getCountry() != null ? s.getCountry().getName() : null)
                .address(s.getAddress())
                .shortDescriptionUid(s.getShortDescription() != null ? s.getShortDescription().getUid() : null)
                .shortDescriptionName(s.getShortDescription() != null ? s.getShortDescription().getName() : null)
                .description(s.getDescription()).email(s.getEmail()).website(s.getWebsite()).phone(s.getPhone())
                .inn(s.getInn()).ogrn(s.getOgrn()).kpp(s.getKpp())
                .contactPerson(s.getContactPerson()).contactPosition(s.getContactPosition()).contactPhone(s.getContactPhone())
                .director(s.getDirector()).directorPosition(s.getDirectorPosition())
                .bankName(s.getBankName()).bik(s.getBik())
                .correspondentAccount(s.getCorrespondentAccount()).settlementAccount(s.getSettlementAccount())
                .createdAt(s.getCreatedAt()).updatedAt(s.getUpdatedAt())
                .build();
    }
}
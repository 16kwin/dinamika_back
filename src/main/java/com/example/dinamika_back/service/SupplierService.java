// SupplierService.java — ПОЛНЫЙ ФАЙЛ
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
    private final SprBrandRepository brandRepository;
    private final SprSupplierDescriptionTypeRepository descriptionTypeRepository;
    private final SprSupplierImageRepository imageRepository;
    private final SprSupplierDocumentRepository documentRepository;
    private final RegSupplierRatingRepository ratingRepository;
    private final RegSupplierIntegrationRepository integrationRepository;

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
        Integer code = maxCode + 1;
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

    // ==================== Сохранение ====================

    @Transactional
    public void saveDraft(SupplierSaveRequest request) {
        SprSupplier supplier = supplierRepository.findById(request.getUid())
                .orElseGet(() -> {
                    SprSupplier newSupplier = new SprSupplier();
                    newSupplier.setUid(request.getUid());
                    newSupplier.setCode(request.getCode());
                    return newSupplier;
                });

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
        if (request.getBrandUid() != null) {
            supplier.setBrand(brandRepository.findById(request.getBrandUid()).orElse(null));
        }
        if (request.getShortDescriptionUid() != null) {
            supplier.setShortDescription(descriptionTypeRepository.findById(request.getShortDescriptionUid()).orElse(null));
        }

        supplierRepository.save(supplier);
    }

    // ==================== Удаление ====================

    @Transactional
    public void deleteSupplier(UUID uid) {
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

        SprSupplierImage image = new SprSupplierImage();
        image.setUid(UUID.randomUUID());
        image.setSupplier(supplier);
        image.setFilePath(fileName);
        image.setOriginalName(file.getOriginalFilename());
        image.setSortOrder(0);
        imageRepository.save(image);

        return new SupplierMediaDTO(
                image.getUid(), supplierUid, fileName,
                file.getOriginalFilename(),
                getFileUrl(supplierUid, fileName), 0);
    }

    @Transactional
    public void deleteImage(UUID uid) {
        SprSupplierImage image = imageRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Изображение не найдено: " + uid));
        UUID supplierUid = image.getSupplier().getUid();
        deleteFile(supplierUid, image.getFilePath());
        imageRepository.delete(image);
    }

    // ==================== ДОКУМЕНТЫ ====================

    public List<SupplierDocumentDTO> getDocuments(UUID supplierUid) {
        return documentRepository.findBySupplierUidOrderByCreatedAtDesc(supplierUid).stream()
                .map(doc -> new SupplierDocumentDTO(
                        doc.getUid(),
                        doc.getSupplier().getUid(),
                        doc.getDocumentName(),
                        doc.getFilePath(),
                        doc.getOriginalName(),
                        getFileUrl(supplierUid, doc.getFilePath()),
                        doc.getCreatedAt()))
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

        return new SupplierDocumentDTO(
                document.getUid(),
                supplierUid,
                document.getDocumentName(),
                document.getFilePath(),
                document.getOriginalName(),
                getFileUrl(supplierUid, fileName),
                document.getCreatedAt());
    }

    @Transactional
    public void deleteDocument(UUID uid) {
        SprSupplierDocument document = documentRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Документ не найден: " + uid));
        UUID supplierUid = document.getSupplier().getUid();
        deleteFile(supplierUid, document.getFilePath());
        documentRepository.delete(document);
    }

    // ==================== РЕЙТИНГ ====================

    public List<SupplierRatingDTO> getRatings(UUID supplierUid) {
        return ratingRepository.findBySupplierUidOrderByCreatedAtDesc(supplierUid).stream()
                .map(r -> new SupplierRatingDTO(
                        r.getUid(),
                        r.getSupplier().getUid(),
                        r.getRating(),
                        r.getComment(),
                        r.getAuthor(),
                        r.getCreatedAt()))
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

        return new SupplierRatingDTO(
                rating.getUid(),
                supplierUid,
                rating.getRating(),
                rating.getComment(),
                rating.getAuthor(),
                rating.getCreatedAt());
    }

    @Transactional
    public void deleteRating(UUID ratingUid) {
        ratingRepository.deleteById(ratingUid);
    }

    // ==================== ИНТЕГРАЦИЯ ====================

    public List<SupplierIntegrationDTO> getIntegrations(UUID supplierUid) {
        return integrationRepository.findBySupplierUidOrderByCreatedAtDesc(supplierUid).stream()
                .map(i -> new SupplierIntegrationDTO(
                        i.getUid(),
                        i.getSupplier().getUid(),
                        i.getEvent(),
                        i.getExchangeType(),
                        i.getDirection(),
                        i.getProtocol(),
                        i.getTargetSystem(),
                        i.getCreatedAt()))
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

        return new SupplierIntegrationDTO(
                integration.getUid(),
                supplierUid,
                integration.getEvent(),
                integration.getExchangeType(),
                integration.getDirection(),
                integration.getProtocol(),
                integration.getTargetSystem(),
                integration.getCreatedAt());
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
                    files.forEach(f -> {
                        try { Files.deleteIfExists(f); } catch (IOException ignored) {}
                    });
                }
                Files.deleteIfExists(dir);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ==================== DTO конвертер ====================

    private SprSupplierDTO toDTO(SprSupplier s) {
        return SprSupplierDTO.builder()
                .uid(s.getUid())
                .code(s.getCode())
                .name(s.getName())
                .countryUid(s.getCountry() != null ? s.getCountry().getUid() : null)
                .countryName(s.getCountry() != null ? s.getCountry().getName() : null)
                .address(s.getAddress())
                .shortDescriptionUid(s.getShortDescription() != null ? s.getShortDescription().getUid() : null)
                .shortDescriptionName(s.getShortDescription() != null ? s.getShortDescription().getName() : null)
                .description(s.getDescription())
                .email(s.getEmail())
                .website(s.getWebsite())
                .phone(s.getPhone())
                .brandUid(s.getBrand() != null ? s.getBrand().getUid() : null)
                .brandName(s.getBrand() != null ? s.getBrand().getName() : null)
                .inn(s.getInn())
                .ogrn(s.getOgrn())
                .kpp(s.getKpp())
                .contactPerson(s.getContactPerson())
                .contactPosition(s.getContactPosition())
                .contactPhone(s.getContactPhone())
                .director(s.getDirector())
                .directorPosition(s.getDirectorPosition())
                .bankName(s.getBankName())
                .bik(s.getBik())
                .correspondentAccount(s.getCorrespondentAccount())
                .settlementAccount(s.getSettlementAccount())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
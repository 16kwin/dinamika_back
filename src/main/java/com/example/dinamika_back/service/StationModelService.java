// StationModelService.java — ПОЛНЫЙ ФАЙЛ
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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationModelService {

    private final StationModelRepository modelRepository;
    private final StationTypeRepository typeRepository;
    private final StationManufacturerRepository manufacturerRepository;
    private final StationModelImageRepository imageRepository;

    private static final String UPLOAD_DIR = "uploads/station-models/";

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
        // Постамат
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
                    sb.append("\"id\":\"").append(col).append("-").append(row).append("\",");
                    sb.append("\"column\":").append(col).append(",");
                    sb.append("\"row\":").append(row);
                    sb.append("}");
                }
            }
            sb.append("]");
            sb.append("}");
            return sb.toString();
        }

        // Барабанный
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
                        sb.append("\"id\":\"").append(drum).append("-").append(col).append("-").append(row).append("\",");
                        sb.append("\"drum\":").append(drum).append(",");
                        sb.append("\"column\":").append(col).append(",");
                        sb.append("\"row\":").append(row);
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
        model.setCellsStructure(generateCellsStructure(request));

        if (request.getTypeId() != null) {
            model.setType(typeRepository.findById(request.getTypeId())
                    .orElseThrow(() -> new RuntimeException("Тип станции не найден: " + request.getTypeId())));
        }
        if (request.getManufacturerId() != null) {
            model.setManufacturer(manufacturerRepository.findById(request.getManufacturerId())
                    .orElseThrow(() -> new RuntimeException("Производитель не найден: " + request.getManufacturerId())));
        }

        model = modelRepository.save(model);
        return toDTO(model);
    }

    @Transactional
    public StationModelDto update(UUID uid, UpdateStationModelRequest request) {
        StationModel model = modelRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Модель станции не найдена: " + uid));

        if (request.getName() != null && !request.getName().isBlank()) {
            model.setName(request.getName());
        }
        if (request.getArticle() != null) {
            model.setArticle(request.getArticle());
        }
        if (request.getRevision() != null) {
            model.setRevision(request.getRevision());
        }
        if (request.getPurpose() != null) {
            model.setPurpose(request.getPurpose());
        }
        if (request.getTypeId() != null) {
            model.setType(typeRepository.findById(request.getTypeId())
                    .orElseThrow(() -> new RuntimeException("Тип станции не найден: " + request.getTypeId())));
        }
        if (request.getManufacturerId() != null) {
            model.setManufacturer(manufacturerRepository.findById(request.getManufacturerId())
                    .orElseThrow(() -> new RuntimeException("Производитель не найден: " + request.getManufacturerId())));
        }

        // Обновление сетки
        CreateStationModelRequest createReq = new CreateStationModelRequest();
        createReq.setColumns(request.getColumns());
        createReq.setCellsPerColumn(request.getCellsPerColumn());
        createReq.setDrums(request.getDrums());
        createReq.setColumnsPerDrum(request.getColumnsPerDrum());
        createReq.setRowsPerColumn(request.getRowsPerColumn());
        String newStructure = generateCellsStructure(createReq);
        if (newStructure != null) {
            model.setCellsStructure(newStructure);
        }

        model = modelRepository.save(model);
        return toDTO(model);
    }

    @Transactional
    public void delete(UUID uid) {
        StationModel model = modelRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Модель станции не найдена: " + uid));
        deleteAllImages(uid);
        modelRepository.delete(model);
    }

    // Изображения

    public List<StationModelImageDto> getImages(UUID modelUid) {
        return imageRepository.findByModelUidOrderBySortOrderAsc(modelUid).stream()
                .map(img -> new StationModelImageDto(
                        img.getUid(),
                        img.getModel().getUid(),
                        img.getFilePath(),
                        img.getOriginalName(),
                        getFileUrl(modelUid, img.getFilePath()),
                        img.getSortOrder()))
                .collect(Collectors.toList());
    }

    @Transactional
    public StationModelImageDto uploadImage(UUID modelUid, MultipartFile file) throws IOException {
        StationModel model = modelRepository.findById(modelUid)
                .orElseThrow(() -> new RuntimeException("Модель станции не найдена: " + modelUid));

        String fileName = saveFile(modelUid, file);

        StationModelImage image = new StationModelImage();
        image.setUid(UUID.randomUUID());
        image.setModel(model);
        image.setFilePath(fileName);
        image.setOriginalName(file.getOriginalFilename());
        image.setSortOrder(0);
        image = imageRepository.save(image);

        return new StationModelImageDto(
                image.getUid(), modelUid, fileName,
                file.getOriginalFilename(),
                getFileUrl(modelUid, fileName), 0);
    }

    @Transactional
    public void deleteImage(UUID imageUid) {
        StationModelImage image = imageRepository.findById(imageUid)
                .orElseThrow(() -> new RuntimeException("Изображение не найдено: " + imageUid));
        UUID modelUid = image.getModel().getUid();
        deleteFile(modelUid, image.getFilePath());
        imageRepository.delete(image);
    }

    private void deleteAllImages(UUID modelUid) {
        List<StationModelImage> images = imageRepository.findByModelUidOrderBySortOrderAsc(modelUid);
        for (StationModelImage img : images) {
            deleteFile(modelUid, img.getFilePath());
        }
        imageRepository.deleteByModelUid(modelUid);
        try {
            Path dir = Path.of(UPLOAD_DIR, modelUid.toString());
            if (Files.exists(dir)) {
                Files.deleteIfExists(dir);
            }
        } catch (IOException ignored) {}
    }

    private String saveFile(UUID modelUid, MultipartFile file) throws IOException {
        Path dir = Path.of(UPLOAD_DIR, modelUid.toString());
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        String extension = getFileExtension(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + extension;
        Path filePath = dir.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        return fileName;
    }

    private void deleteFile(UUID modelUid, String fileName) {
        try {
            Path filePath = Path.of(UPLOAD_DIR, modelUid.toString(), fileName);
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {}
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }

    private String getFileUrl(UUID modelUid, String filePath) {
        return "/uploads/station-models/" + modelUid + "/" + filePath;
    }

    private StationModelDto toDTO(StationModel model) {
        String typeName = model.getType() != null ? model.getType().getName() : null;
        UUID typeId = model.getType() != null ? model.getType().getUid() : null;
        String manufacturerName = model.getManufacturer() != null ? model.getManufacturer().getName() : null;
        UUID manufacturerId = model.getManufacturer() != null ? model.getManufacturer().getUid() : null;

        return new StationModelDto(
                model.getUid(),
                model.getCode(),
                model.getName(),
                model.getArticle(),
                model.getRevision(),
                typeId,
                typeName,
                manufacturerId,
                manufacturerName,
                model.getPurpose(),
                model.getCellsStructure()
        );
    }
}
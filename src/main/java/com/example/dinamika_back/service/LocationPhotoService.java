package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.LocationPhotoDTO;
import com.example.dinamika_back.dto.StationPositionDTO;
import com.example.dinamika_back.model.Location;
import com.example.dinamika_back.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LocationPhotoService {

    private final LocationRepository locationRepository;
    private final StationPositionService stationPositionService;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    private static final String[] ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".gif"};
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    // Загрузка/обновление фото для локации
    public LocationPhotoDTO uploadPhoto(Integer locationId, MultipartFile file) throws IOException {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("Локация не найдена с ID: " + locationId));

        validateFile(file);
        deleteOldPhotoFromDisk(location);

        String fileExtension = getFileExtension(file.getOriginalFilename()).toLowerCase();
        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;

        String locationDir = uploadDir + "/locations/" + locationId + "/";
        Path uploadPath = Paths.get(locationDir);

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        Path filePath = uploadPath.resolve(uniqueFileName);
        Files.copy(file.getInputStream(), filePath);

        location.setPhotoFileName(uniqueFileName);
        location.setPhotoFilePath("locations/" + locationId + "/");
        location.setPhotoFileExtension(fileExtension);
        
        locationRepository.save(location);

        return createPhotoDTO(location);
    }

    // Получение информации о фото - СТАНЦИИ ТОЛЬКО ЕСЛИ ЕСТЬ ФОТО
    public LocationPhotoDTO getPhotoInfo(Integer locationId) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("Локация не найдена с ID: " + locationId));

        if (location.getPhotoFileName() == null) {
            return null; // Если фото нет - возвращаем null
        }

        return createPhotoDTO(location);
    }

    // Удаление фото
    public void deletePhoto(Integer locationId) throws IOException {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("Локация не найдена с ID: " + locationId));

        if (location.getPhotoFileName() == null) {
            throw new IllegalArgumentException("У локации нет фото для удаления");
        }

        deleteOldPhotoFromDisk(location);

        location.setPhotoFileName(null);
        location.setPhotoFilePath(null);
        location.setPhotoFileExtension(null);
        
        locationRepository.save(location);
    }

    // Создание DTO с фото и станциями
    private LocationPhotoDTO createPhotoDTO(Location location) {
        LocationPhotoDTO dto = new LocationPhotoDTO();
        dto.setPhotoFileName(location.getPhotoFileName());
        dto.setPhotoFilePath(location.getPhotoFilePath());
        dto.setPhotoFileExtension(location.getPhotoFileExtension());
        
        // Формируем URL
        if (location.getPhotoFileName() != null && location.getPhotoFilePath() != null) {
            dto.setPhotoUrl("/uploads/" + location.getPhotoFilePath() + location.getPhotoFileName());
        } else {
            dto.setPhotoUrl(null);
        }
        
        // ДОБАВЛЯЕМ СТАНЦИИ ТОЛЬКО ЕСЛИ ЕСТЬ ФОТО
        List<StationPositionDTO> stations = stationPositionService.getPositionsByLocation(location.getId());
        dto.setStations(stations);
        
        return dto;
    }

    // Валидация файла
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Файл пустой");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Размер файла превышает 10MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("Имя файла не может быть пустым");
        }

        String fileExtension = getFileExtension(originalFilename).toLowerCase();
        if (!isValidExtension(fileExtension)) {
            throw new IllegalArgumentException("Недопустимый формат файла. Разрешены: " + 
                    String.join(", ", ALLOWED_EXTENSIONS));
        }
    }

    // Удаление старого файла с диска
    private void deleteOldPhotoFromDisk(Location location) throws IOException {
        if (location.getPhotoFileName() != null && location.getPhotoFilePath() != null) {
            Path oldFilePath = Paths.get(uploadDir + "/" + location.getPhotoFilePath() + location.getPhotoFileName());
            if (Files.exists(oldFilePath)) {
                Files.delete(oldFilePath);
            }
        }
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex);
    }

    private boolean isValidExtension(String extension) {
        for (String allowed : ALLOWED_EXTENSIONS) {
            if (allowed.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }
}
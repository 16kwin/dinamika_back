package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.CreateStationDTO;
import com.example.dinamika_back.model.Location;
import com.example.dinamika_back.model.Station;
import com.example.dinamika_back.model.StationModel;
import com.example.dinamika_back.repository.LocationRepository;
import com.example.dinamika_back.repository.StationModelRepository;
import com.example.dinamika_back.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MakeStationService {
    
    private final StationRepository stationRepository;
    private final StationModelRepository stationModelRepository;
    private final LocationRepository locationRepository;
    
    @Transactional
    public Station createStation(CreateStationDTO dto) {
        // Валидация
        validateStationDTO(dto);
        
        // Находим или создаем модель
        StationModel model = findOrCreateModel(dto.getModelNumber());
        
        // Находим локации
        Location level1Factory = getLocation(dto.getLevel1FactoryId(), 1, "Завод");
        Location level2Object = getOptionalLocation(dto.getLevel2ObjectId(), 2, "Цех");
        Location level3Zone = getOptionalLocation(dto.getLevel3ZoneId(), 3, "Участок");
        
        // Проверка серийного номера
        if (stationRepository.existsBySerialNumberOfTheStation(dto.getSerialNumber())) {
            throw new IllegalArgumentException("Станция с таким серийным номером уже существует: " + dto.getSerialNumber());
        }
        
        // Проверка иерархии
        validateLocationHierarchy(level2Object, level3Zone);
        
        // Создание станции
        Station station = new Station(
            dto.getStationName(),
            model,
            dto.getSerialNumber(),
            dto.getCurrentCapacity(),
            dto.getIpAddress(),
            level1Factory,
            level2Object,
            level3Zone
        );
        
        return stationRepository.save(station);
    }
    
    private void validateStationDTO(CreateStationDTO dto) {
        if (dto.getStationName() == null || dto.getStationName().trim().isEmpty()) {
            throw new IllegalArgumentException("Название станции обязательно");
        }
        
        if (dto.getModelNumber() == null) {
            throw new IllegalArgumentException("Номер модели обязателен");
        }
        
        if (dto.getSerialNumber() == null) {
            throw new IllegalArgumentException("Серийный номер обязателен");
        }
        
        if (dto.getCurrentCapacity() == null) {
            throw new IllegalArgumentException("Текущая емкость обязательна");
        }
        
        if (dto.getIpAddress() == null || dto.getIpAddress().trim().isEmpty()) {
            throw new IllegalArgumentException("IP адрес обязателен");
        }
        
        if (dto.getLevel1FactoryId() == null) {
            throw new IllegalArgumentException("ID завода обязателен");
        }
        
        // Проверка IP адреса (базовая)
        if (!dto.getIpAddress().matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
            throw new IllegalArgumentException("Неверный формат IP адреса");
        }
    }
    
    private StationModel findOrCreateModel(Integer modelNumber) {
        return stationModelRepository.findByModelNumber(modelNumber)
                .orElseGet(() -> {
                    StationModel newModel = new StationModel(modelNumber);
                    return stationModelRepository.save(newModel);
                });
    }
    
    private Location getLocation(Integer locationId, Integer expectedLevel, String locationType) {
        if (locationId == null) {
            throw new IllegalArgumentException(locationType + " ID обязателен");
        }
        
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException(locationType + " не найден"));
        
        if (!location.getLevel().equals(expectedLevel)) {
            throw new IllegalArgumentException("Локация должна быть " + locationType.toLowerCase() + " (уровень " + expectedLevel + ")");
        }
        
        return location;
    }
    
    private Location getOptionalLocation(Integer locationId, Integer expectedLevel, String locationType) {
        if (locationId == null) {
            return null;
        }
        
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException(locationType + " не найден"));
        
        if (!location.getLevel().equals(expectedLevel)) {
            throw new IllegalArgumentException("Локация должна быть " + locationType.toLowerCase() + " (уровень " + expectedLevel + ")");
        }
        
        return location;
    }
    
    private void validateLocationHierarchy(Location level2Object, Location level3Zone) {
        // Если есть участок, должен быть цех
        if (level3Zone != null && level2Object == null) {
            throw new IllegalArgumentException("Не может быть участка без цеха");
        }
        
        // Если есть цех, должен быть завод (уже проверено через level1FactoryId)
    }
}
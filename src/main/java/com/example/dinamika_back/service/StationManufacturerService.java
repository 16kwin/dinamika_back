// StationManufacturerService.java
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.CreateStationManufacturerRequest;
import com.example.dinamika_back.dto.StationManufacturerDto;
import com.example.dinamika_back.dto.UpdateStationManufacturerRequest;
import com.example.dinamika_back.model.StationManufacturer;
import com.example.dinamika_back.repository.StationManufacturerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationManufacturerService {

    private final StationManufacturerRepository manufacturerRepository;

    public List<StationManufacturerDto> getAll() {
        return manufacturerRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public StationManufacturerDto getById(UUID uid) {
        StationManufacturer manufacturer = manufacturerRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Производитель не найден: " + uid));
        return toDTO(manufacturer);
    }

    @Transactional
    public StationManufacturerDto create(CreateStationManufacturerRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Наименование производителя обязательно");
        }
        if (manufacturerRepository.existsByName(request.getName())) {
            throw new RuntimeException("Производитель с таким именем уже существует: " + request.getName());
        }
        StationManufacturer manufacturer = new StationManufacturer();
        manufacturer.setUid(UUID.randomUUID());
        manufacturer.setName(request.getName());
        manufacturer.setDescription(request.getDescription());
        manufacturer = manufacturerRepository.save(manufacturer);
        return toDTO(manufacturer);
    }

    @Transactional
    public StationManufacturerDto update(UUID uid, UpdateStationManufacturerRequest request) {
        StationManufacturer manufacturer = manufacturerRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Производитель не найден: " + uid));
        if (request.getName() != null && !request.getName().isBlank()
                && !manufacturer.getName().equals(request.getName())
                && manufacturerRepository.existsByName(request.getName())) {
            throw new RuntimeException("Производитель с таким именем уже существует: " + request.getName());
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            manufacturer.setName(request.getName());
        }
        if (request.getDescription() != null) {
            manufacturer.setDescription(request.getDescription());
        }
        manufacturer = manufacturerRepository.save(manufacturer);
        return toDTO(manufacturer);
    }

    @Transactional
    public void delete(UUID uid) {
        StationManufacturer manufacturer = manufacturerRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Производитель не найден: " + uid));
        manufacturerRepository.delete(manufacturer);
    }

    private StationManufacturerDto toDTO(StationManufacturer manufacturer) {
        return new StationManufacturerDto(manufacturer.getUid(), manufacturer.getName(), manufacturer.getDescription());
    }
}
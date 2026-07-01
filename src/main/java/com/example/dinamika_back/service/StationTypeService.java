// StationTypeService.java
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.CreateStationTypeRequest;
import com.example.dinamika_back.dto.StationTypeDto;
import com.example.dinamika_back.dto.UpdateStationTypeRequest;
import com.example.dinamika_back.model.StationType;
import com.example.dinamika_back.repository.StationTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationTypeService {

    private final StationTypeRepository stationTypeRepository;

    public List<StationTypeDto> getAll() {
        return stationTypeRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public StationTypeDto getById(UUID uid) {
        StationType type = stationTypeRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Тип станции не найден: " + uid));
        return toDTO(type);
    }

    @Transactional
    public StationTypeDto create(CreateStationTypeRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Наименование типа обязательно");
        }
        if (stationTypeRepository.existsByName(request.getName())) {
            throw new RuntimeException("Тип станции с таким именем уже существует: " + request.getName());
        }
        StationType type = new StationType();
        type.setUid(UUID.randomUUID());
        type.setName(request.getName());
        type.setDescription(request.getDescription());
        type = stationTypeRepository.save(type);
        return toDTO(type);
    }

    @Transactional
    public StationTypeDto update(UUID uid, UpdateStationTypeRequest request) {
        StationType type = stationTypeRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Тип станции не найден: " + uid));
        if (request.getName() != null && !request.getName().isBlank()
                && !type.getName().equals(request.getName())
                && stationTypeRepository.existsByName(request.getName())) {
            throw new RuntimeException("Тип станции с таким именем уже существует: " + request.getName());
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            type.setName(request.getName());
        }
        if (request.getDescription() != null) {
            type.setDescription(request.getDescription());
        }
        type = stationTypeRepository.save(type);
        return toDTO(type);
    }

    @Transactional
    public void delete(UUID uid) {
        StationType type = stationTypeRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Тип станции не найден: " + uid));
        stationTypeRepository.delete(type);
    }

    private StationTypeDto toDTO(StationType type) {
        return new StationTypeDto(type.getUid(), type.getName(), type.getDescription());
    }
}
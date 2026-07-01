// StationConfigurationService.java
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.*;
import com.example.dinamika_back.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationConfigurationService {

    private final StationConfigurationRepository configurationRepository;
    private final StationModelRepository modelRepository;

    public List<StationConfigurationDto> getAll() {
        return configurationRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<StationConfigurationDto> getByModelId(UUID modelId) {
        return configurationRepository.findByModelUidOrderByNameAsc(modelId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public StationConfigurationDto getById(UUID uid) {
        StationConfiguration config = configurationRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Конфигурация не найдена: " + uid));
        return toDTO(config);
    }

    @Transactional
    public StationConfigurationDto create(CreateStationConfigurationRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Наименование конфигурации обязательно");
        }
        if (request.getModelId() == null) {
            throw new RuntimeException("Модель станции обязательна");
        }

        StationModel model = modelRepository.findById(request.getModelId())
                .orElseThrow(() -> new RuntimeException("Модель станции не найдена: " + request.getModelId()));

        if (configurationRepository.existsByNameAndModelUid(request.getName(), request.getModelId())) {
            throw new RuntimeException("Конфигурация с таким именем уже существует для этой модели");
        }

        StationConfiguration config = new StationConfiguration();
        config.setUid(request.getUid() != null ? request.getUid() : UUID.randomUUID());
        config.setName(request.getName());
        config.setModel(model);
        config.setCellsStructure(request.getCellsStructure());

        config = configurationRepository.save(config);
        return toDTO(config);
    }

    @Transactional
    public StationConfigurationDto update(UUID uid, UpdateStationConfigurationRequest request) {
        StationConfiguration config = configurationRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Конфигурация не найдена: " + uid));

        if (request.getName() != null && !request.getName().isBlank()) {
            if (!config.getName().equals(request.getName())
                    && request.getModelId() != null
                    && configurationRepository.existsByNameAndModelUid(request.getName(), request.getModelId())) {
                throw new RuntimeException("Конфигурация с таким именем уже существует для этой модели");
            }
            config.setName(request.getName());
        }

        if (request.getModelId() != null) {
            StationModel model = modelRepository.findById(request.getModelId())
                    .orElseThrow(() -> new RuntimeException("Модель станции не найдена: " + request.getModelId()));
            config.setModel(model);
        }

        if (request.getCellsStructure() != null) {
            config.setCellsStructure(request.getCellsStructure());
        }

        config = configurationRepository.save(config);
        return toDTO(config);
    }

    @Transactional
    public void delete(UUID uid) {
        StationConfiguration config = configurationRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Конфигурация не найдена: " + uid));
        configurationRepository.delete(config);
    }

    private StationConfigurationDto toDTO(StationConfiguration config) {
        String modelName = config.getModel() != null ? config.getModel().getName() : null;
        UUID modelId = config.getModel() != null ? config.getModel().getUid() : null;

        return new StationConfigurationDto(
                config.getUid(),
                config.getName(),
                modelId,
                modelName,
                config.getCellsStructure()
        );
    }
}
// StationCrudService.java — ПОЛНЫЙ ФАЙЛ
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.CreateStationRequest;
import com.example.dinamika_back.dto.StationDto;
import com.example.dinamika_back.dto.UpdateStationRequest;
import com.example.dinamika_back.model.*;
import com.example.dinamika_back.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationCrudService {

    private final StationRepository stationRepository;
    private final HoldingRepository holdingRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final WorkshopRepository workshopRepository;
    private final SectionRepository sectionRepository;
    private final StationModelRepository modelRepository;
    private final StationConfigurationRepository configurationRepository;

    public List<StationDto> getAll() {
        return stationRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public StationDto getByUid(String uid) {
        Station station = stationRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Станция не найдена: " + uid));
        return toDTO(station);
    }

    public Integer generateCode() {
        Integer maxCode = stationRepository.findMaxCode();
        return maxCode != null ? maxCode + 1 : 1;
    }

    @Transactional
    public StationDto create(CreateStationRequest request) {
        Station station = new Station();
        station.setUid(request.getUid());
        station.setCode(request.getUid() != null && stationRepository.existsByUid(request.getUid())
                ? (stationRepository.findMaxCode() != null ? stationRepository.findMaxCode() + 1 : 1)
                : generateCode());
        station.setName(request.getName());
        station.setDescription(request.getDescription());
        station.setProductionDate(request.getProductionDate());
        station.setSerialNumber(request.getSerialNumber());

        if (request.getHoldingId() != null) {
            Holding holding = holdingRepository.findById(request.getHoldingId())
                    .orElseThrow(() -> new RuntimeException("Холдинг не найден: " + request.getHoldingId()));
            station.setHolding(holding);
        }

        if (request.getEnterpriseId() != null) {
            Enterprise enterprise = enterpriseRepository.findById(request.getEnterpriseId())
                    .orElseThrow(() -> new RuntimeException("Предприятие не найдено: " + request.getEnterpriseId()));
            station.setEnterprise(enterprise);
        }

        if (request.getWorkshopId() != null) {
            Workshop workshop = workshopRepository.findById(request.getWorkshopId())
                    .orElseThrow(() -> new RuntimeException("Цех не найден: " + request.getWorkshopId()));
            station.setWorkshop(workshop);
        }

        if (request.getSectionId() != null) {
            Section section = sectionRepository.findById(request.getSectionId())
                    .orElseThrow(() -> new RuntimeException("Участок не найден: " + request.getSectionId()));
            station.setSection(section);
        }

        station.setStatus(request.getStatus() != null
                ? StationStatus.valueOf(request.getStatus())
                : StationStatus.WORKING);

        if (request.getModelId() != null) {
            StationModel model = modelRepository.findById(java.util.UUID.fromString(request.getModelId()))
                    .orElseThrow(() -> new RuntimeException("Модель не найдена: " + request.getModelId()));
            station.setModel(model);
        }

        if (request.getConfigurationUid() != null) {
            StationConfiguration config = configurationRepository.findById(java.util.UUID.fromString(request.getConfigurationUid()))
                    .orElseThrow(() -> new RuntimeException("Конфигурация не найдена: " + request.getConfigurationUid()));
            station.setConfiguration(config);
        }

        station.setParentUid(request.getParentUid());
        station.setIsAdditionalModule(request.getIsAdditionalModule() != null ? request.getIsAdditionalModule() : false);
        station.setHasAdditionalModule(request.getHasAdditionalModule() != null ? request.getHasAdditionalModule() : false);
        station.setHasError(request.getHasError() != null ? request.getHasError() : false);
        station.setIsTmc(request.getIsTmc() != null ? request.getIsTmc() : false);
        station.setIsSgd(request.getIsSgd() != null ? request.getIsSgd() : false);
        station.setIsOk(request.getIsOk() != null ? request.getIsOk() : false);
        station.setIpAddress(request.getIpAddress());
        station.setNetworkPort(request.getNetworkPort());

        station.setTotalCells(0);
        station.setFilledCells(0);
        station.setTemplateNomenclatureCount(0);
        station.setRemainingNomenclatureCount(0);
        station.setMaxReadyParts(0);
        station.setReadyPartsCount(0);

        station = stationRepository.save(station);
        return toDTO(station);
    }

    @Transactional
    public StationDto update(String uid, UpdateStationRequest request) {
        Station station = stationRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Станция не найдена: " + uid));

        if (request.getName() != null) station.setName(request.getName());
        if (request.getDescription() != null) station.setDescription(request.getDescription());
        if (request.getProductionDate() != null) station.setProductionDate(request.getProductionDate());
        if (request.getSerialNumber() != null) station.setSerialNumber(request.getSerialNumber());

        if (request.getHoldingId() != null) {
            Holding holding = holdingRepository.findById(request.getHoldingId())
                    .orElseThrow(() -> new RuntimeException("Холдинг не найден: " + request.getHoldingId()));
            station.setHolding(holding);
        }

        if (request.getEnterpriseId() != null) {
            Enterprise enterprise = enterpriseRepository.findById(request.getEnterpriseId())
                    .orElseThrow(() -> new RuntimeException("Предприятие не найдено: " + request.getEnterpriseId()));
            station.setEnterprise(enterprise);
        }

        if (request.getWorkshopId() != null) {
            Workshop workshop = workshopRepository.findById(request.getWorkshopId())
                    .orElseThrow(() -> new RuntimeException("Цех не найден: " + request.getWorkshopId()));
            station.setWorkshop(workshop);
        }

        if (request.getSectionId() != null) {
            Section section = sectionRepository.findById(request.getSectionId())
                    .orElseThrow(() -> new RuntimeException("Участок не найден: " + request.getSectionId()));
            station.setSection(section);
        }

        if (request.getStatus() != null) station.setStatus(StationStatus.valueOf(request.getStatus()));

        if (request.getModelId() != null) {
            StationModel model = modelRepository.findById(java.util.UUID.fromString(request.getModelId()))
                    .orElseThrow(() -> new RuntimeException("Модель не найдена: " + request.getModelId()));
            station.setModel(model);
        }

        if (request.getConfigurationUid() != null) {
            StationConfiguration config = configurationRepository.findById(java.util.UUID.fromString(request.getConfigurationUid()))
                    .orElseThrow(() -> new RuntimeException("Конфигурация не найдена: " + request.getConfigurationUid()));
            station.setConfiguration(config);
        }

        if (request.getParentUid() != null) station.setParentUid(request.getParentUid());
        if (request.getIsAdditionalModule() != null) station.setIsAdditionalModule(request.getIsAdditionalModule());
        if (request.getHasAdditionalModule() != null) station.setHasAdditionalModule(request.getHasAdditionalModule());
        if (request.getHasError() != null) station.setHasError(request.getHasError());
        if (request.getIsTmc() != null) station.setIsTmc(request.getIsTmc());
        if (request.getIsSgd() != null) station.setIsSgd(request.getIsSgd());
        if (request.getIsOk() != null) station.setIsOk(request.getIsOk());
        if (request.getIpAddress() != null) station.setIpAddress(request.getIpAddress());
        if (request.getNetworkPort() != null) station.setNetworkPort(request.getNetworkPort());

        station = stationRepository.save(station);
        return toDTO(station);
    }

    @Transactional
    public void delete(String uid) {
        Station station = stationRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Станция не найдена: " + uid));
        stationRepository.delete(station);
    }

    private StationDto toDTO(Station station) {
        StationDto dto = new StationDto();
        dto.setUid(station.getUid());
        dto.setCode(station.getCode());
        dto.setName(station.getName());
        dto.setDescription(station.getDescription());
        dto.setProductionDate(station.getProductionDate());
        dto.setSerialNumber(station.getSerialNumber());

        if (station.getModel() != null) {
            dto.setModelId(station.getModel().getUid().toString());
            dto.setModelName(station.getModel().getName());
            dto.setArticle(station.getModel().getArticle());
            dto.setRevision(station.getModel().getRevision());
            if (station.getModel().getType() != null) {
                dto.setStationType(station.getModel().getType().getName());
                dto.setStationTypeUid(station.getModel().getType().getUid().toString());
            }
        }

        dto.setHoldingId(station.getHolding() != null ? station.getHolding().getId() : null);
        dto.setHoldingName(station.getHolding() != null ? station.getHolding().getName() : null);
        dto.setEnterpriseId(station.getEnterprise() != null ? station.getEnterprise().getId() : null);
        dto.setEnterpriseName(station.getEnterprise() != null ? station.getEnterprise().getName() : null);
        dto.setWorkshopId(station.getWorkshop() != null ? station.getWorkshop().getId() : null);
        dto.setWorkshopName(station.getWorkshop() != null ? station.getWorkshop().getName() : null);
        dto.setSectionId(station.getSection() != null ? station.getSection().getId() : null);
        dto.setSectionName(station.getSection() != null ? station.getSection().getName() : null);

        dto.setStatus(station.getStatus() != null ? station.getStatus().name() : null);

        if (station.getConfiguration() != null) {
            dto.setConfigurationUid(station.getConfiguration().getUid().toString());
            dto.setConfigurationName(station.getConfiguration().getName());
        }

        dto.setParentUid(station.getParentUid());
        dto.setIsAdditionalModule(station.getIsAdditionalModule());
        dto.setHasAdditionalModule(station.getHasAdditionalModule());
        dto.setHasError(station.getHasError());
        dto.setIsTmc(station.getIsTmc());
        dto.setIsSgd(station.getIsSgd());
        dto.setIsOk(station.getIsOk());
        dto.setIpAddress(station.getIpAddress());
        dto.setNetworkPort(station.getNetworkPort());

        dto.setActiveTemplateUid(station.getActiveTemplate() != null ? station.getActiveTemplate().getUid().toString() : null);
        dto.setActiveTemplateName(station.getActiveTemplate() != null ? station.getActiveTemplate().getNamePattern() : null);

        return dto;
    }
}
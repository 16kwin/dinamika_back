// StationService.java
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.StationDynamicDto;
import com.example.dinamika_back.dto.StationStaticDto;
import com.example.dinamika_back.model.Station;
import com.example.dinamika_back.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationService {
    
    private final StationRepository stationRepository;
    
    public List<StationStaticDto> getAllStaticStations() {
        return stationRepository.findAllStaticData().stream()
                .map(this::convertToStaticDto)
                .collect(Collectors.toList());
    }
    
    public List<StationDynamicDto> getAllDynamicStations() {
        return stationRepository.findAll().stream()
                .map(this::convertToDynamicDto)
                .collect(Collectors.toList());
    }
    
    public StationDynamicDto getDynamicByUid(String uid) {
        return stationRepository.findByUid(uid)
                .map(this::convertToDynamicDto)
                .orElse(null);
    }
    
    public StationStaticDto getStaticByUid(String uid) {
        return stationRepository.findByUid(uid)
                .map(this::convertToStaticDto)
                .orElse(null);
    }
    
    private StationStaticDto convertToStaticDto(Station station) {
        return new StationStaticDto(
                station.getUid(),
                station.getName(),
                station.getWorkshop(),
                station.getSection(),
                station.getStatus() != null ? station.getStatus().name() : null,
                station.getStationType() != null ? station.getStationType().name() : null,
                station.getParentUid(),
                station.getHasError(),
                station.getIsTmc(),
                station.getIsSgd(),
                station.getIsOk()
        );
    }
    
    private StationDynamicDto convertToDynamicDto(Station station) {
        int totalCells = station.getTotalCells() != null ? station.getTotalCells() : 0;
        int filledCells = station.getFilledCells() != null ? station.getFilledCells() : 0;
        int templateNomenclatureCount = station.getTemplateNomenclatureCount() != null ? station.getTemplateNomenclatureCount() : 0;
        int remainingNomenclatureCount = station.getRemainingNomenclatureCount() != null ? station.getRemainingNomenclatureCount() : 0;
        int maxReadyParts = station.getMaxReadyParts() != null ? station.getMaxReadyParts() : 0;
        int readyPartsCount = station.getReadyPartsCount() != null ? station.getReadyPartsCount() : 0;
        
        double filledCellsPercent = totalCells > 0 
                ? (filledCells * 100.0) / totalCells 
                : 0.0;
        
        double remainingNomenclaturePercent = templateNomenclatureCount > 0 
                ? (remainingNomenclatureCount * 100.0) / templateNomenclatureCount 
                : 0.0;
        
        double readyPartsPercent = maxReadyParts > 0 
                ? (readyPartsCount * 100.0) / maxReadyParts 
                : 0.0;
        
        return new StationDynamicDto(
                station.getUid(),
                filledCellsPercent,
                remainingNomenclaturePercent,
                readyPartsPercent,
                totalCells,
                filledCells,
                templateNomenclatureCount,
                remainingNomenclatureCount,
                maxReadyParts,
                readyPartsCount
        );
    }
}
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
        double filledCellsPercent = station.getTotalCells() > 0 
                ? (station.getFilledCells() * 100.0) / station.getTotalCells() 
                : 0.0;
        
        double remainingNomenclaturePercent = station.getTemplateNomenclatureCount() > 0 
                ? (station.getRemainingNomenclatureCount() * 100.0) / station.getTemplateNomenclatureCount() 
                : 0.0;
        
        double readyPartsPercent = station.getMaxReadyParts() > 0 
                ? (station.getReadyPartsCount() * 100.0) / station.getMaxReadyParts() 
                : 0.0;
        
        return new StationDynamicDto(
                station.getUid(),
                filledCellsPercent,
                remainingNomenclaturePercent,
                readyPartsPercent
        );
    }
}
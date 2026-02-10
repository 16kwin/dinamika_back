package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.StationPositionDTO;
import com.example.dinamika_back.model.StationPosition;
import com.example.dinamika_back.repository.StationPositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationPositionService {
    
    private final StationPositionRepository stationPositionRepository;
    
    @Transactional
    public StationPositionDTO createOrUpdatePosition(StationPositionDTO positionDTO) {
        // Проверяем, существует ли уже позиция для этой станции на этой локации
        Optional<StationPosition> existingPosition = stationPositionRepository
            .findByStationIdAndLocationId(positionDTO.getStationId(), positionDTO.getLocationId());
        
        StationPosition position;
        if (existingPosition.isPresent()) {
            // Обновляем существующую позицию
            position = existingPosition.get();
            position.setCoordX(positionDTO.getCoordX());
            position.setCoordY(positionDTO.getCoordY());
        } else {
            // Создаем новую позицию
            position = new StationPosition();
            position.setStationId(positionDTO.getStationId());
            position.setLocationId(positionDTO.getLocationId());
            position.setCoordX(positionDTO.getCoordX());
            position.setCoordY(positionDTO.getCoordY());
        }
        
        StationPosition savedPosition = stationPositionRepository.save(position);
        return convertToDTO(savedPosition);
    }
    
    public List<StationPositionDTO> getPositionsByLocation(Integer locationId) {
        return stationPositionRepository.findByLocationId(locationId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public List<StationPositionDTO> getPositionsByStation(Integer stationId) {
        return stationPositionRepository.findByStationId(stationId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public void deletePosition(Integer stationId, Integer locationId) {
        stationPositionRepository.deleteByStationIdAndLocationId(stationId, locationId);
    }
    
    @Transactional
    public void deleteAllPositionsByStation(Integer stationId) {
        stationPositionRepository.deleteByStationId(stationId);
    }
    
    @Transactional
    public void deleteAllPositionsByLocation(Integer locationId) {
        stationPositionRepository.deleteByLocationId(locationId);
    }
    
    public Optional<StationPositionDTO> getPosition(Integer stationId, Integer locationId) {
        return stationPositionRepository.findByStationIdAndLocationId(stationId, locationId)
                .map(this::convertToDTO);
    }
    
    private StationPositionDTO convertToDTO(StationPosition position) {
        StationPositionDTO dto = new StationPositionDTO();
        dto.setStationId(position.getStationId());
        dto.setLocationId(position.getLocationId());
        dto.setCoordX(position.getCoordX());
        dto.setCoordY(position.getCoordY());
        return dto;
    }
    
    private StationPosition convertToEntity(StationPositionDTO dto) {
        StationPosition position = new StationPosition();
        position.setStationId(dto.getStationId());
        position.setLocationId(dto.getLocationId());
        position.setCoordX(dto.getCoordX());
        position.setCoordY(dto.getCoordY());
        return position;
    }
}
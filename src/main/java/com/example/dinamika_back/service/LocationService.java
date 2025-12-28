package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.LocationHierarchyDTO;
import com.example.dinamika_back.dto.StationDTO;
import com.example.dinamika_back.model.Location;
import com.example.dinamika_back.model.LocationDependency;
import com.example.dinamika_back.model.Station;
import com.example.dinamika_back.repository.LocationDependencyRepository;
import com.example.dinamika_back.repository.LocationRepository;
import com.example.dinamika_back.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LocationService {
    
    private final LocationRepository locationRepository;
    private final LocationDependencyRepository locationDependencyRepository;
    private final StationRepository stationRepository;
    
    @Transactional(readOnly = true)
    public List<LocationHierarchyDTO> getLocationHierarchy() {
        List<Location> level1Locations = locationRepository.findByLevelOrderByLocationName(1);
        
        return level1Locations.stream()
                .map(this::buildLocationHierarchy)
                .collect(Collectors.toList());
    }
    
    private LocationHierarchyDTO buildLocationHierarchy(Location location) {
        LocationHierarchyDTO dto = new LocationHierarchyDTO(
            location.getId(),
            location.getLocationName(), 
            location.getLevel()
        );
        
        addStationsToDTO(location, dto);
        addChildLocations(location, dto);
        
        return dto;
    }
    
    private void addStationsToDTO(Location location, LocationHierarchyDTO dto) {
        List<Station> stations;
        
        switch (location.getLevel()) {
            case 1:
                stations = stationRepository.findStationsDirectlyUnderLocation(location.getId());
                break;
            case 2:
                stations = stationRepository.findStationsUnderLevel2Location(location.getId());
                break;
            case 3:
                stations = stationRepository.findStationsUnderLevel3Location(location.getId());
                break;
            default:
                stations = List.of();
        }
        
        List<StationDTO> stationDTOs = stations.stream()
                .map(station -> new StationDTO(
                    station.getUid(),
                    station.getStationName(),
                    station.getStationModel().getModelNumber(),
                    station.getSerialNumberOfTheStation(),
                    station.getCurrentCapacityOfTheStation()
                ))
                .collect(Collectors.toList());
        
        dto.getStations().addAll(stationDTOs);
    }
    
    private void addChildLocations(Location parentLocation, LocationHierarchyDTO parentDTO) {
        if (parentLocation.getLevel() >= 3) {
            return;
        }
        
        List<LocationDependency> dependencies = locationDependencyRepository
                .findByParentLocationId(parentLocation.getId());
        
        dependencies.forEach(dependency -> {
            Location childLocation = dependency.getChildLocation();
            LocationHierarchyDTO childDTO = buildLocationHierarchy(childLocation);
            parentDTO.getChildLocations().add(childDTO);
        });
    }
}
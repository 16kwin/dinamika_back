// StationService.java (обновленный)
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.StationDynamicDto;
import com.example.dinamika_back.dto.StationStaticDto;
import com.example.dinamika_back.dto.UserFilterDTO;
import com.example.dinamika_back.model.Station;
import com.example.dinamika_back.model.StationStatus;
import com.example.dinamika_back.model.StationType;
import com.example.dinamika_back.repository.StationRepository;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationService {
    
    private final StationRepository stationRepository;
    
    public List<StationStaticDto> getFilteredStaticStations(UserFilterDTO filters) {
        Specification<Station> spec = buildSpecification(filters);
        List<Station> stations = stationRepository.findAll(spec);
        
        // Применяем сортировку
        stations = applySorting(stations, filters);
        
        return stations.stream()
                .map(this::convertToStaticDto)
                .collect(Collectors.toList());
    }
    
    public List<StationDynamicDto> getFilteredDynamicStations(UserFilterDTO filters) {
        Specification<Station> spec = buildSpecification(filters);
        List<Station> stations = stationRepository.findAll(spec);
        
        // Применяем сортировку
        stations = applySorting(stations, filters);
        
        return stations.stream()
                .map(this::convertToDynamicDto)
                .collect(Collectors.toList());
    }
    
    public List<StationStaticDto> getAllStaticStations() {
        return stationRepository.findAll().stream()
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
    
    private Specification<Station> buildSpecification(UserFilterDTO filters) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Поиск по названию станции, участка, цеха, предприятия
            if (filters.getSearchQuery() != null && !filters.getSearchQuery().isEmpty()) {
                String searchPattern = "%" + filters.getSearchQuery().toLowerCase() + "%";
                Predicate namePredicate = cb.like(cb.lower(root.get("name")), searchPattern);
                
                Predicate sectionPredicate = cb.like(cb.lower(root.join("section", JoinType.LEFT).get("name")), searchPattern);
                Predicate workshopPredicate = cb.like(cb.lower(root.join("workshop", JoinType.LEFT).get("name")), searchPattern);
                Predicate enterprisePredicate = cb.like(cb.lower(root.join("enterprise", JoinType.LEFT).get("name")), searchPattern);
                
                predicates.add(cb.or(namePredicate, sectionPredicate, workshopPredicate, enterprisePredicate));
            }
            
            // Фильтр по предприятиям
            if (filters.getSelectedEnterprises() != null && !filters.getSelectedEnterprises().isEmpty()) {
                predicates.add(root.get("enterprise").get("id").in(filters.getSelectedEnterprises()));
            }
            
            // Фильтр по цехам
            if (filters.getSelectedWorkshops() != null && !filters.getSelectedWorkshops().isEmpty()) {
                predicates.add(root.get("workshop").get("id").in(filters.getSelectedWorkshops()));
            }
            
            // Фильтр по участкам
            if (filters.getSelectedSections() != null && !filters.getSelectedSections().isEmpty()) {
                predicates.add(root.get("section").get("id").in(filters.getSelectedSections()));
            }
            
            // Фильтр по статусам
            if (filters.getSelectedStatuses() != null && !filters.getSelectedStatuses().isEmpty()) {
                List<StationStatus> statuses = filters.getSelectedStatuses().stream()
                        .map(StationStatus::valueOf)
                        .collect(Collectors.toList());
                predicates.add(root.get("status").in(statuses));
            }
            
            // Минимальный остаток
            if (filters.getMinOstatok() != null && filters.getMinOstatok()) {
                predicates.add(cb.equal(root.get("status"), StationStatus.MINIMAL_STOCK));
            }
            
            // Критический остаток
            if (filters.getCriticalOstatok() != null && filters.getCriticalOstatok()) {
                predicates.add(cb.equal(root.get("status"), StationStatus.CRITICAL_STOCK));
            }
            
            // Фильтр по типам станций
            if (filters.getSelectedTypes() != null && !filters.getSelectedTypes().isEmpty()) {
                List<StationType> types = filters.getSelectedTypes().stream()
                        .map(StationType::valueOf)
                        .collect(Collectors.toList());
                predicates.add(root.get("stationType").in(types));
            }
            
            // Сверхнормы
            if (filters.getOverissue() != null) {
                // Логика для сверхнормы (нужно уточнить какое поле отвечает)
                // predicates.add(cb.equal(root.get("overissue"), filters.getOverissue()));
            }
            
            // Ошибка
            if (filters.getHasError() != null) {
                predicates.add(cb.equal(root.get("hasError"), filters.getHasError()));
            }
            
            // ТМЦ
            if (filters.getIsTmc() != null) {
                predicates.add(cb.equal(root.get("isTmc"), filters.getIsTmc()));
            }
            
            // СГД
            if (filters.getIsSgd() != null) {
                predicates.add(cb.equal(root.get("isSgd"), filters.getIsSgd()));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
    
    private List<Station> applySorting(List<Station> stations, UserFilterDTO filters) {
        if (filters.getSortOption() == null) {
            return stations;
        }
        
        switch (filters.getSortOption()) {
            case NAME_ASC:
                stations.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                break;
            case NAME_DESC:
                stations.sort((a, b) -> b.getName().compareToIgnoreCase(a.getName()));
                break;
            case PLACEMENT:
                stations.sort((a, b) -> {
                    String sectionA = a.getSection() != null ? a.getSection().getName() : "";
                    String sectionB = b.getSection() != null ? b.getSection().getName() : "";
                    String workshopA = a.getWorkshop() != null ? a.getWorkshop().getName() : "";
                    String workshopB = b.getWorkshop() != null ? b.getWorkshop().getName() : "";
                    String enterpriseA = a.getEnterprise() != null ? a.getEnterprise().getName() : "";
                    String enterpriseB = b.getEnterprise() != null ? b.getEnterprise().getName() : "";
                    
                    int sectionCompare = sectionA.compareTo(sectionB);
                    if (sectionCompare != 0) return sectionCompare;
                    
                    int workshopCompare = workshopA.compareTo(workshopB);
                    if (workshopCompare != 0) return workshopCompare;
                    
                    return enterpriseA.compareTo(enterpriseB);
                });
                break;
            case STATUS:
                stations.sort((a, b) -> {
                    int priorityA = getStatusPriority(a.getStatus());
                    int priorityB = getStatusPriority(b.getStatus());
                    return Integer.compare(priorityA, priorityB);
                });
                break;
            case TYPE_PRIORITY:
                stations.sort((a, b) -> {
                    int priorityA = getTypePriority(a);
                    int priorityB = getTypePriority(b);
                    return Integer.compare(priorityA, priorityB);
                });
                break;
        }
        
        return stations;
    }
    
    private int getStatusPriority(StationStatus status) {
        if (status == null) return 999;
        return switch (status) {
            case WORKING -> 1;
            case OFFLINE -> 2;
            case MINIMAL_STOCK -> 3;
            case CRITICAL_STOCK -> 4;
        };
    }
    
    private int getTypePriority(Station station) {
        if (station.getIsTmc() != null && station.getIsTmc()) return 1;
        if (station.getIsSgd() != null && station.getIsSgd()) return 2;
        return 3;
    }
    
    private StationStaticDto convertToStaticDto(Station station) {
        String workshopName = station.getWorkshop() != null ? station.getWorkshop().getName() : null;
        String sectionName = station.getSection() != null ? station.getSection().getName() : null;
        Long enterpriseId = station.getEnterprise() != null ? station.getEnterprise().getId() : null;
        Long workshopId = station.getWorkshop() != null ? station.getWorkshop().getId() : null;
        Long sectionId = station.getSection() != null ? station.getSection().getId() : null;
        
        return new StationStaticDto(
                station.getUid(),
                station.getName(),
                workshopName,
                sectionName,
                enterpriseId,
                workshopId,
                sectionId,
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
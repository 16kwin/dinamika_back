// src/main/java/com/example/dinamika_back/service/DashboardService.java
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.DashboardStatsDTO;
import com.example.dinamika_back.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {
    
    private final StationRepository stationRepository;
    
    @Transactional(readOnly = true)
    public DashboardStatsDTO getDashboardStats() {
        // Вариант 1: Использование отдельных запросов (более понятный)
        Long totalStations = stationRepository.countAllStations();
        Long totalFullness = stationRepository.sumFullness();
        Long totalCapacity = stationRepository.sumCapacity();
        Long totalIssued = stationRepository.sumIssued();
        Long totalIssuedOverNorm = stationRepository.sumIssuedOverNorm();
        
        return new DashboardStatsDTO(
            totalStations,
            totalFullness,
            totalCapacity,
            totalIssued,
            totalIssuedOverNorm
        );
        
    }
}
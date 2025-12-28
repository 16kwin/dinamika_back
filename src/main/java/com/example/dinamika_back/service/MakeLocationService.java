package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.CreateLocationDTO;
import com.example.dinamika_back.model.Location;
import com.example.dinamika_back.model.LocationDependency;
import com.example.dinamika_back.repository.LocationDependencyRepository;
import com.example.dinamika_back.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MakeLocationService {
    
    private final LocationRepository locationRepository;
    private final LocationDependencyRepository locationDependencyRepository;
    
    @Transactional
    public Location createLocation(CreateLocationDTO dto) {
        // Валидация
        if (dto.getLevel() == null || dto.getLevel() < 1 || dto.getLevel() > 3) {
            throw new IllegalArgumentException("Level должен быть 1, 2 или 3");
        }
        
        if (dto.getLevel() == 1 && dto.getParentId() != null) {
            throw new IllegalArgumentException("Завод не может иметь родителя");
        }
        
        if (dto.getLevel() > 1 && dto.getParentId() == null) {
            throw new IllegalArgumentException("Цех и участок должны иметь родителя");
        }
        
        // Проверка существования имени
        boolean exists = locationRepository.existsByLevelAndLocationName(dto.getLevel(), dto.getName());
        if (exists) {
            throw new IllegalArgumentException("Локация с таким именем уже существует на этом уровне");
        }
        
        // Создание локации
        Location location = new Location(dto.getName(), dto.getLevel());
        location = locationRepository.save(location);
        
        // Создание зависимости для цеха/участка
        if (dto.getLevel() > 1) {
            Location parent = locationRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Родительская локация не найдена"));
            
            // Проверка уровня родителя
            if (parent.getLevel() != dto.getLevel() - 1) {
                throw new IllegalArgumentException("Неверный уровень родительской локации. Ожидался уровень " + (dto.getLevel() - 1));
            }
            
            // Проверка что у родителя еще нет такого ребенка
            boolean childExists = locationDependencyRepository.existsByChildLocation(location);
            if (childExists) {
                throw new IllegalArgumentException("Эта локация уже привязана к родителю");
            }
            
            LocationDependency dependency = new LocationDependency(location, parent);
            locationDependencyRepository.save(dependency);
        }
        
        return location;
    }
}
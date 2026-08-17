// StationConfigurationColumnSettingsService.java — ПОЛНЫЙ ФАЙЛ
package com.example.dinamika_back.service;

import com.example.dinamika_back.model.UserStationConfigurationColumnSettings;
import com.example.dinamika_back.repository.UserStationConfigurationColumnSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StationConfigurationColumnSettingsService {

    private final UserStationConfigurationColumnSettingsRepository repository;

    public String getColumnsJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserStationConfigurationColumnSettings::getColumnsJson)
                .orElse(null);
    }

    public String getFiltersJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserStationConfigurationColumnSettings::getFiltersJson)
                .orElse("{}");
    }

    public String getSortJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserStationConfigurationColumnSettings::getSortJson)
                .orElse("{}");
    }

    @Transactional
    public void saveColumnsJson(Integer userId, String columnsJson) {
        UserStationConfigurationColumnSettings settings = getOrCreate(userId);
        settings.setColumnsJson(columnsJson);
        repository.save(settings);
    }

    @Transactional
    public void saveFiltersJson(Integer userId, String filtersJson) {
        UserStationConfigurationColumnSettings settings = getOrCreate(userId);
        settings.setFiltersJson(filtersJson);
        repository.save(settings);
    }

    @Transactional
    public void saveSortJson(Integer userId, String sortJson) {
        UserStationConfigurationColumnSettings settings = getOrCreate(userId);
        settings.setSortJson(sortJson);
        repository.save(settings);
    }

    private UserStationConfigurationColumnSettings getOrCreate(Integer userId) {
        return repository.findByUserId(userId)
                .orElseGet(() -> UserStationConfigurationColumnSettings.builder()
                        .userId(userId)
                        .columnsJson("{}")
                        .filtersJson("{}")
                        .sortJson("{}")
                        .build());
    }
}
// EmployeeColumnSettingsService.java
package com.example.dinamika_back.service;

import com.example.dinamika_back.model.UserEmployeeColumnSettings;
import com.example.dinamika_back.repository.UserEmployeeColumnSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeColumnSettingsService {

    private final UserEmployeeColumnSettingsRepository repository;

    public String getColumnsJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserEmployeeColumnSettings::getColumnsJson)
                .orElse(null);
    }

    public String getFiltersJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserEmployeeColumnSettings::getFiltersJson)
                .orElse("{}");
    }

    public String getSortJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserEmployeeColumnSettings::getSortJson)
                .orElse("{}");
    }

    @Transactional
    public void saveColumnsJson(Integer userId, String columnsJson) {
        UserEmployeeColumnSettings settings = getOrCreate(userId);
        settings.setColumnsJson(columnsJson);
        repository.save(settings);
    }

    @Transactional
    public void saveFiltersJson(Integer userId, String filtersJson) {
        UserEmployeeColumnSettings settings = getOrCreate(userId);
        settings.setFiltersJson(filtersJson);
        repository.save(settings);
    }

    @Transactional
    public void saveSortJson(Integer userId, String sortJson) {
        UserEmployeeColumnSettings settings = getOrCreate(userId);
        settings.setSortJson(sortJson);
        repository.save(settings);
    }

    @Transactional
    public void saveAllJson(Integer userId, String columnsJson, String filtersJson, String sortJson) {
        UserEmployeeColumnSettings settings = getOrCreate(userId);
        settings.setColumnsJson(columnsJson);
        settings.setFiltersJson(filtersJson);
        settings.setSortJson(sortJson);
        repository.save(settings);
    }

    private UserEmployeeColumnSettings getOrCreate(Integer userId) {
        return repository.findByUserId(userId)
                .orElseGet(() -> UserEmployeeColumnSettings.builder()
                        .userId(userId)
                        .columnsJson("{}")
                        .filtersJson("{}")
                        .sortJson("{}")
                        .build());
    }
}
// NomenclatureColumnSettingsService.java
package com.example.dinamika_back.service;

import com.example.dinamika_back.model.UserNomenclatureColumnSettings;
import com.example.dinamika_back.repository.UserNomenclatureColumnSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NomenclatureColumnSettingsService {

    private final UserNomenclatureColumnSettingsRepository repository;

    public String getColumnsJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserNomenclatureColumnSettings::getColumnsJson)
                .orElse(null);
    }

    public String getFiltersJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserNomenclatureColumnSettings::getFiltersJson)
                .orElse("{}");
    }

    public String getSortJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserNomenclatureColumnSettings::getSortJson)
                .orElse("{}");
    }

    public String getCurrentPathJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserNomenclatureColumnSettings::getCurrentPathJson)
                .orElse("[]");
    }

    @Transactional
    public void saveColumnsJson(Integer userId, String columnsJson) {
        UserNomenclatureColumnSettings settings = getOrCreate(userId);
        settings.setColumnsJson(columnsJson);
        repository.save(settings);
    }

    @Transactional
    public void saveFiltersJson(Integer userId, String filtersJson) {
        UserNomenclatureColumnSettings settings = getOrCreate(userId);
        settings.setFiltersJson(filtersJson);
        repository.save(settings);
    }

    @Transactional
    public void saveSortJson(Integer userId, String sortJson) {
        UserNomenclatureColumnSettings settings = getOrCreate(userId);
        settings.setSortJson(sortJson);
        repository.save(settings);
    }

    @Transactional
    public void saveCurrentPathJson(Integer userId, String currentPathJson) {
        UserNomenclatureColumnSettings settings = getOrCreate(userId);
        settings.setCurrentPathJson(currentPathJson);
        repository.save(settings);
    }

    @Transactional
    public void saveAllJson(Integer userId, String columnsJson, String filtersJson, String sortJson, String currentPathJson) {
        UserNomenclatureColumnSettings settings = getOrCreate(userId);
        settings.setColumnsJson(columnsJson);
        settings.setFiltersJson(filtersJson);
        settings.setSortJson(sortJson);
        settings.setCurrentPathJson(currentPathJson);
        repository.save(settings);
    }

    private UserNomenclatureColumnSettings getOrCreate(Integer userId) {
        return repository.findByUserId(userId)
                .orElseGet(() -> UserNomenclatureColumnSettings.builder()
                        .userId(userId)
                        .columnsJson("{}")
                        .filtersJson("{}")
                        .sortJson("{}")
                        .currentPathJson("[]")
                        .build());
    }
}
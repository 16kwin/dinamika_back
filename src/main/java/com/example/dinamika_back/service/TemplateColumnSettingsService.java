// TemplateColumnSettingsService.java
package com.example.dinamika_back.service;

import com.example.dinamika_back.model.UserTemplateColumnSettings;
import com.example.dinamika_back.repository.UserTemplateColumnSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TemplateColumnSettingsService {

    private final UserTemplateColumnSettingsRepository repository;

    public String getColumnsJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserTemplateColumnSettings::getColumnsJson)
                .orElse(null);
    }

    public String getFiltersJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserTemplateColumnSettings::getFiltersJson)
                .orElse("{}");
    }

    public String getSortJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserTemplateColumnSettings::getSortJson)
                .orElse("{}");
    }

    public String getCurrentPathJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserTemplateColumnSettings::getCurrentPathJson)
                .orElse("[]");
    }

    @Transactional
    public void saveColumnsJson(Integer userId, String columnsJson) {
        UserTemplateColumnSettings settings = getOrCreate(userId);
        settings.setColumnsJson(columnsJson);
        repository.save(settings);
    }

    @Transactional
    public void saveFiltersJson(Integer userId, String filtersJson) {
        UserTemplateColumnSettings settings = getOrCreate(userId);
        settings.setFiltersJson(filtersJson);
        repository.save(settings);
    }

    @Transactional
    public void saveSortJson(Integer userId, String sortJson) {
        UserTemplateColumnSettings settings = getOrCreate(userId);
        settings.setSortJson(sortJson);
        repository.save(settings);
    }

    @Transactional
    public void saveCurrentPathJson(Integer userId, String currentPathJson) {
        UserTemplateColumnSettings settings = getOrCreate(userId);
        settings.setCurrentPathJson(currentPathJson);
        repository.save(settings);
    }

    private UserTemplateColumnSettings getOrCreate(Integer userId) {
        return repository.findByUserId(userId)
                .orElseGet(() -> UserTemplateColumnSettings.builder()
                        .userId(userId)
                        .columnsJson("{}")
                        .filtersJson("{}")
                        .sortJson("{}")
                        .currentPathJson("[]")
                        .build());
    }
}
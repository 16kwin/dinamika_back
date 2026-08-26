package com.example.dinamika_back.service;

import com.example.dinamika_back.model.UserTypeMaterialColumnSettings;
import com.example.dinamika_back.repository.UserTypeMaterialColumnSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TypeMaterialColumnSettingsService {

    private final UserTypeMaterialColumnSettingsRepository repository;

    public String getColumnsJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserTypeMaterialColumnSettings::getColumnsJson)
                .orElse(null);
    }

    public String getSortJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserTypeMaterialColumnSettings::getSortJson)
                .orElse("{}");
    }

    @Transactional
    public void saveColumnsJson(Integer userId, String columnsJson) {
        UserTypeMaterialColumnSettings settings = getOrCreate(userId);
        settings.setColumnsJson(columnsJson);
        repository.save(settings);
    }

    @Transactional
    public void saveSortJson(Integer userId, String sortJson) {
        UserTypeMaterialColumnSettings settings = getOrCreate(userId);
        settings.setSortJson(sortJson);
        repository.save(settings);
    }

    @Transactional
    public void saveAllJson(Integer userId, String columnsJson, String sortJson) {
        UserTypeMaterialColumnSettings settings = getOrCreate(userId);
        settings.setColumnsJson(columnsJson);
        settings.setSortJson(sortJson);
        repository.save(settings);
    }

    private UserTypeMaterialColumnSettings getOrCreate(Integer userId) {
        return repository.findByUserId(userId)
                .orElseGet(() -> UserTypeMaterialColumnSettings.builder()
                        .userId(userId)
                        .columnsJson("{}")
                        .sortJson("{}")
                        .build());
    }
}
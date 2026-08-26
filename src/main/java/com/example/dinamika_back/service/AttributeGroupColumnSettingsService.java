package com.example.dinamika_back.service;

import com.example.dinamika_back.model.UserAttributeGroupColumnSettings;
import com.example.dinamika_back.repository.UserAttributeGroupColumnSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttributeGroupColumnSettingsService {

    private final UserAttributeGroupColumnSettingsRepository repository;

    public String getColumnsJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserAttributeGroupColumnSettings::getColumnsJson)
                .orElse(null);
    }

    public String getSortJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserAttributeGroupColumnSettings::getSortJson)
                .orElse("{}");
    }

    @Transactional
    public void saveColumnsJson(Integer userId, String columnsJson) {
        UserAttributeGroupColumnSettings settings = repository.findByUserId(userId).orElse(null);
        if (settings == null) {
            settings = UserAttributeGroupColumnSettings.builder()
                    .userId(userId)
                    .columnsJson(columnsJson)
                    .sortJson("{}")
                    .build();
        } else {
            settings.setColumnsJson(columnsJson);
        }
        repository.save(settings);
    }

    @Transactional
    public void saveSortJson(Integer userId, String sortJson) {
        UserAttributeGroupColumnSettings settings = repository.findByUserId(userId).orElse(null);
        if (settings == null) {
            settings = UserAttributeGroupColumnSettings.builder()
                    .userId(userId)
                    .columnsJson("{}")
                    .sortJson(sortJson)
                    .build();
        } else {
            settings.setSortJson(sortJson);
        }
        repository.save(settings);
    }

    @Transactional
    public void saveAllJson(Integer userId, String columnsJson, String sortJson) {
        UserAttributeGroupColumnSettings settings = repository.findByUserId(userId).orElse(null);
        if (settings == null) {
            settings = UserAttributeGroupColumnSettings.builder()
                    .userId(userId)
                    .columnsJson(columnsJson)
                    .sortJson(sortJson)
                    .build();
        } else {
            settings.setColumnsJson(columnsJson);
            settings.setSortJson(sortJson);
        }
        repository.save(settings);
    }
}
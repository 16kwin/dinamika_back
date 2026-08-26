package com.example.dinamika_back.service;

import com.example.dinamika_back.model.UserProductionDirectionColumnSettings;
import com.example.dinamika_back.repository.UserProductionDirectionColumnSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductionDirectionColumnSettingsService {

    private final UserProductionDirectionColumnSettingsRepository repository;

    public String getColumnsJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserProductionDirectionColumnSettings::getColumnsJson)
                .orElse(null);
    }

    public String getSortJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserProductionDirectionColumnSettings::getSortJson)
                .orElse("{}");
    }

    @Transactional
    public void saveColumnsJson(Integer userId, String columnsJson) {
        UserProductionDirectionColumnSettings settings = repository.findByUserId(userId).orElse(null);
        if (settings == null) {
            settings = UserProductionDirectionColumnSettings.builder()
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
        UserProductionDirectionColumnSettings settings = repository.findByUserId(userId).orElse(null);
        if (settings == null) {
            settings = UserProductionDirectionColumnSettings.builder()
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
        UserProductionDirectionColumnSettings settings = repository.findByUserId(userId).orElse(null);
        if (settings == null) {
            settings = UserProductionDirectionColumnSettings.builder()
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
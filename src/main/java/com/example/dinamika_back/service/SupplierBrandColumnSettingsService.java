package com.example.dinamika_back.service;

import com.example.dinamika_back.model.UserSupplierBrandColumnSettings;
import com.example.dinamika_back.repository.UserSupplierBrandColumnSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupplierBrandColumnSettingsService {

    private final UserSupplierBrandColumnSettingsRepository repository;

    public String getColumnsJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserSupplierBrandColumnSettings::getColumnsJson)
                .orElse(null);
    }

    public String getFiltersJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserSupplierBrandColumnSettings::getFiltersJson)
                .orElse("{}");
    }

    public String getSortJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserSupplierBrandColumnSettings::getSortJson)
                .orElse("{}");
    }

    @Transactional
    public void saveColumnsJson(Integer userId, String columnsJson) {
        UserSupplierBrandColumnSettings settings = repository.findByUserId(userId).orElse(null);
        if (settings == null) {
            settings = UserSupplierBrandColumnSettings.builder()
                    .userId(userId)
                    .columnsJson(columnsJson)
                    .filtersJson("{}")
                    .sortJson("{}")
                    .build();
        } else {
            settings.setColumnsJson(columnsJson);
        }
        repository.save(settings);
    }

    @Transactional
    public void saveFiltersJson(Integer userId, String filtersJson) {
        UserSupplierBrandColumnSettings settings = repository.findByUserId(userId).orElse(null);
        if (settings == null) {
            settings = UserSupplierBrandColumnSettings.builder()
                    .userId(userId)
                    .columnsJson("{}")
                    .filtersJson(filtersJson)
                    .sortJson("{}")
                    .build();
        } else {
            settings.setFiltersJson(filtersJson);
        }
        repository.save(settings);
    }

    @Transactional
    public void saveSortJson(Integer userId, String sortJson) {
        UserSupplierBrandColumnSettings settings = repository.findByUserId(userId).orElse(null);
        if (settings == null) {
            settings = UserSupplierBrandColumnSettings.builder()
                    .userId(userId)
                    .columnsJson("{}")
                    .filtersJson("{}")
                    .sortJson(sortJson)
                    .build();
        } else {
            settings.setSortJson(sortJson);
        }
        repository.save(settings);
    }

    @Transactional
    public void saveAllJson(Integer userId, String columnsJson, String filtersJson, String sortJson) {
        UserSupplierBrandColumnSettings settings = repository.findByUserId(userId).orElse(null);
        if (settings == null) {
            settings = UserSupplierBrandColumnSettings.builder()
                    .userId(userId)
                    .columnsJson(columnsJson)
                    .filtersJson(filtersJson)
                    .sortJson(sortJson)
                    .build();
        } else {
            settings.setColumnsJson(columnsJson);
            settings.setFiltersJson(filtersJson);
            settings.setSortJson(sortJson);
        }
        repository.save(settings);
    }
}
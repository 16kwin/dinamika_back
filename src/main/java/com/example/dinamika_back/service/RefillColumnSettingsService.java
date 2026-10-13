package com.example.dinamika_back.service;

import com.example.dinamika_back.model.UserRefillColumnSettings;
import com.example.dinamika_back.repository.UserRefillColumnSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefillColumnSettingsService {

    private final UserRefillColumnSettingsRepository repository;

    public String getColumnsJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserRefillColumnSettings::getColumnsJson)
                .orElse(null);
    }

    public String getFiltersJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserRefillColumnSettings::getFiltersJson)
                .orElse("{}");
    }

    public String getSortJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserRefillColumnSettings::getSortJson)
                .orElse("{}");
    }

    @Transactional
    public void saveColumnsJson(Integer userId, String columnsJson) {
        UserRefillColumnSettings s = getOrCreate(userId);
        s.setColumnsJson(columnsJson);
        repository.save(s);
    }

    @Transactional
    public void saveFiltersJson(Integer userId, String filtersJson) {
        UserRefillColumnSettings s = getOrCreate(userId);
        s.setFiltersJson(filtersJson);
        repository.save(s);
    }

    @Transactional
    public void saveSortJson(Integer userId, String sortJson) {
        UserRefillColumnSettings s = getOrCreate(userId);
        s.setSortJson(sortJson);
        repository.save(s);
    }

    private UserRefillColumnSettings getOrCreate(Integer userId) {
        return repository.findByUserId(userId)
                .orElseGet(() -> UserRefillColumnSettings.builder()
                        .userId(userId)
                        .columnsJson("{}")
                        .filtersJson("{}")
                        .sortJson("{}")
                        .build());
    }
}
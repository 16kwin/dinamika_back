package com.example.dinamika_back.service;

import com.example.dinamika_back.model.UserStockLevelControlColumnSettings;
import com.example.dinamika_back.repository.UserStockLevelControlColumnSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockLevelControlColumnSettingsService {

    private final UserStockLevelControlColumnSettingsRepository repository;

    private UserStockLevelControlColumnSettings getOrCreate(Integer userId) {
        return repository.findByUserId(userId).orElseGet(() -> {
            UserStockLevelControlColumnSettings s = UserStockLevelControlColumnSettings.builder()
                    .userId(userId)
                    .columnsJson("{}")
                    .filtersJson("{}")
                    .sortJson("{}")
                    .build();
            return repository.save(s);
        });
    }

    public String getColumnsJson(Integer userId) {
        return getOrCreate(userId).getColumnsJson();
    }

    public String getFiltersJson(Integer userId) {
        String v = getOrCreate(userId).getFiltersJson();
        return v != null ? v : "{}";
    }

    public String getSortJson(Integer userId) {
        String v = getOrCreate(userId).getSortJson();
        return v != null ? v : "{}";
    }

    @Transactional
    public void saveColumnsJson(Integer userId, String json) {
        UserStockLevelControlColumnSettings s = getOrCreate(userId);
        s.setColumnsJson(json);
        repository.save(s);
    }

    @Transactional
    public void saveFiltersJson(Integer userId, String json) {
        UserStockLevelControlColumnSettings s = getOrCreate(userId);
        s.setFiltersJson(json);
        repository.save(s);
    }

    @Transactional
    public void saveSortJson(Integer userId, String json) {
        UserStockLevelControlColumnSettings s = getOrCreate(userId);
        s.setSortJson(json);
        repository.save(s);
    }

    @Transactional
    public void saveAllJson(Integer userId, String columnsJson, String filtersJson, String sortJson) {
        UserStockLevelControlColumnSettings s = getOrCreate(userId);
        if (columnsJson != null) s.setColumnsJson(columnsJson);
        if (filtersJson != null) s.setFiltersJson(filtersJson);
        if (sortJson != null) s.setSortJson(sortJson);
        repository.save(s);
    }
}
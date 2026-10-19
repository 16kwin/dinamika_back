// MoveOrderColumnSettingsService.java
package com.example.dinamika_back.service;

import com.example.dinamika_back.model.UserMoveOrderColumnSettings;
import com.example.dinamika_back.repository.UserMoveOrderColumnSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MoveOrderColumnSettingsService {

    private final UserMoveOrderColumnSettingsRepository repository;

    public String getColumnsJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserMoveOrderColumnSettings::getColumnsJson)
                .orElse(null);
    }

    public String getFiltersJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserMoveOrderColumnSettings::getFiltersJson)
                .orElse("{}");
    }

    public String getSortJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserMoveOrderColumnSettings::getSortJson)
                .orElse("{}");
    }

    @Transactional
    public void saveColumnsJson(Integer userId, String columnsJson) {
        UserMoveOrderColumnSettings s = getOrCreate(userId);
        s.setColumnsJson(columnsJson);
        repository.save(s);
    }

    @Transactional
    public void saveFiltersJson(Integer userId, String filtersJson) {
        UserMoveOrderColumnSettings s = getOrCreate(userId);
        s.setFiltersJson(filtersJson);
        repository.save(s);
    }

    @Transactional
    public void saveSortJson(Integer userId, String sortJson) {
        UserMoveOrderColumnSettings s = getOrCreate(userId);
        s.setSortJson(sortJson);
        repository.save(s);
    }

    private UserMoveOrderColumnSettings getOrCreate(Integer userId) {
        return repository.findByUserId(userId)
                .orElseGet(() -> UserMoveOrderColumnSettings.builder()
                        .userId(userId)
                        .columnsJson("{}")
                        .filtersJson("{}")
                        .sortJson("{}")
                        .build());
    }
}
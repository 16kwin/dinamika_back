// LocationColumnSettingsService.java
package com.example.dinamika_back.service;

import com.example.dinamika_back.model.UserLocationColumnSettings;
import com.example.dinamika_back.repository.UserLocationColumnSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocationColumnSettingsService {

    private final UserLocationColumnSettingsRepository repository;

    public String getColumnsJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserLocationColumnSettings::getColumnsJson)
                .orElse(null);
    }

    @Transactional
    public void saveColumnsJson(Integer userId, String columnsJson) {
        UserLocationColumnSettings settings = getOrCreate(userId);
        settings.setColumnsJson(columnsJson);
        repository.save(settings);
    }

    private UserLocationColumnSettings getOrCreate(Integer userId) {
        return repository.findByUserId(userId)
                .orElseGet(() -> UserLocationColumnSettings.builder()
                        .userId(userId)
                        .columnsJson("{}")
                        .filtersJson("{}")
                        .sortJson("{}")
                        .build());
    }
}
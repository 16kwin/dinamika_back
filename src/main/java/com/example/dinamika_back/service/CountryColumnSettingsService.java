// CountryColumnSettingsService.java
package com.example.dinamika_back.service;

import com.example.dinamika_back.model.UserCountryColumnSettings;
import com.example.dinamika_back.repository.UserCountryColumnSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CountryColumnSettingsService {

    private final UserCountryColumnSettingsRepository repository;

    public String getColumnsJson(Integer userId) {
        return repository.findByUserId(userId)
                .map(UserCountryColumnSettings::getColumnsJson)
                .orElse(null);
    }

    @Transactional
    public void saveColumnsJson(Integer userId, String columnsJson) {
        UserCountryColumnSettings settings = getOrCreate(userId);
        settings.setColumnsJson(columnsJson);
        repository.save(settings);
    }

    private UserCountryColumnSettings getOrCreate(Integer userId) {
        return repository.findByUserId(userId)
                .orElseGet(() -> UserCountryColumnSettings.builder()
                        .userId(userId)
                        .columnsJson("{}")
                        .filtersJson("{}")
                        .sortJson("{}")
                        .build());
    }
}
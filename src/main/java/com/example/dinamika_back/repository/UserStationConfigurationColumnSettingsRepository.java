// UserStationConfigurationColumnSettingsRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserStationConfigurationColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserStationConfigurationColumnSettingsRepository extends JpaRepository<UserStationConfigurationColumnSettings, Long> {
    Optional<UserStationConfigurationColumnSettings> findByUserId(Integer userId);
}
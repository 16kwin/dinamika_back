// UserStationModelColumnSettingsRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserStationModelColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserStationModelColumnSettingsRepository extends JpaRepository<UserStationModelColumnSettings, Long> {
    Optional<UserStationModelColumnSettings> findByUserId(Integer userId);
}
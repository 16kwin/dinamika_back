// UserStationColumnSettingsRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserStationColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserStationColumnSettingsRepository extends JpaRepository<UserStationColumnSettings, Long> {
    Optional<UserStationColumnSettings> findByUserId(Integer userId);
}
// UserStationManufacturerColumnSettingsRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserStationManufacturerColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserStationManufacturerColumnSettingsRepository extends JpaRepository<UserStationManufacturerColumnSettings, Long> {
    Optional<UserStationManufacturerColumnSettings> findByUserId(Integer userId);
}
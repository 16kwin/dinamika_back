// UserStationTypeColumnSettingsRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserStationTypeColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserStationTypeColumnSettingsRepository extends JpaRepository<UserStationTypeColumnSettings, Long> {
    Optional<UserStationTypeColumnSettings> findByUserId(Integer userId);
}
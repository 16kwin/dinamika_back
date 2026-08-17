// UserWorkshopColumnSettingsRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserWorkshopColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserWorkshopColumnSettingsRepository extends JpaRepository<UserWorkshopColumnSettings, Long> {
    Optional<UserWorkshopColumnSettings> findByUserId(Integer userId);
}
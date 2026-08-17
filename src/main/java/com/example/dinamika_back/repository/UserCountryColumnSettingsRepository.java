// UserCountryColumnSettingsRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserCountryColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserCountryColumnSettingsRepository extends JpaRepository<UserCountryColumnSettings, Long> {
    Optional<UserCountryColumnSettings> findByUserId(Integer userId);
}
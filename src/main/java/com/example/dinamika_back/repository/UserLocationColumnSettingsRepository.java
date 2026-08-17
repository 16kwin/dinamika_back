// UserLocationColumnSettingsRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserLocationColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserLocationColumnSettingsRepository extends JpaRepository<UserLocationColumnSettings, Long> {
    Optional<UserLocationColumnSettings> findByUserId(Integer userId);
}
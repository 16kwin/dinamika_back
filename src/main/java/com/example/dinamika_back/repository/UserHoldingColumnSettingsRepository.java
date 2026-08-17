// UserHoldingColumnSettingsRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserHoldingColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserHoldingColumnSettingsRepository extends JpaRepository<UserHoldingColumnSettings, Long> {
    Optional<UserHoldingColumnSettings> findByUserId(Integer userId);
}
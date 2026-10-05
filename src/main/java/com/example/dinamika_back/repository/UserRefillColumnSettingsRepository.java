package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserRefillColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRefillColumnSettingsRepository extends JpaRepository<UserRefillColumnSettings, Long> {
    Optional<UserRefillColumnSettings> findByUserId(Integer userId);
}
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserMeasureColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserMeasureColumnSettingsRepository extends JpaRepository<UserMeasureColumnSettings, Long> {
    Optional<UserMeasureColumnSettings> findByUserId(Integer userId);
}
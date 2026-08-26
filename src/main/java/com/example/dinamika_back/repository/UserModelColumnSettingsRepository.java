package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserModelColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserModelColumnSettingsRepository extends JpaRepository<UserModelColumnSettings, Long> {
    Optional<UserModelColumnSettings> findByUserId(Integer userId);
}
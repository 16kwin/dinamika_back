package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserBrandColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserBrandColumnSettingsRepository extends JpaRepository<UserBrandColumnSettings, Long> {
    Optional<UserBrandColumnSettings> findByUserId(Integer userId);
}
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserManufacturerColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserManufacturerColumnSettingsRepository extends JpaRepository<UserManufacturerColumnSettings, Long> {
    Optional<UserManufacturerColumnSettings> findByUserId(Integer userId);
}
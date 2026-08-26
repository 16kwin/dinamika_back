package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserUnitColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserUnitColumnSettingsRepository extends JpaRepository<UserUnitColumnSettings, Long> {
    Optional<UserUnitColumnSettings> findByUserId(Integer userId);
}
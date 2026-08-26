package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserProductionDirectionColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProductionDirectionColumnSettingsRepository extends JpaRepository<UserProductionDirectionColumnSettings, Long> {
    Optional<UserProductionDirectionColumnSettings> findByUserId(Integer userId);
}
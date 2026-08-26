package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserTypeMaterialColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserTypeMaterialColumnSettingsRepository extends JpaRepository<UserTypeMaterialColumnSettings, Long> {
    Optional<UserTypeMaterialColumnSettings> findByUserId(Integer userId);
}
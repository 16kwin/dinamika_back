package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserTypePurposeColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserTypePurposeColumnSettingsRepository extends JpaRepository<UserTypePurposeColumnSettings, Long> {
    Optional<UserTypePurposeColumnSettings> findByUserId(Integer userId);
}
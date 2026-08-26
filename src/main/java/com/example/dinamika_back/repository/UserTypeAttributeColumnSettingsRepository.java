package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserTypeAttributeColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserTypeAttributeColumnSettingsRepository extends JpaRepository<UserTypeAttributeColumnSettings, Long> {
    Optional<UserTypeAttributeColumnSettings> findByUserId(Integer userId);
}
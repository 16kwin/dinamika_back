package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserAttributeGroupColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserAttributeGroupColumnSettingsRepository extends JpaRepository<UserAttributeGroupColumnSettings, Long> {
    Optional<UserAttributeGroupColumnSettings> findByUserId(Integer userId);
}
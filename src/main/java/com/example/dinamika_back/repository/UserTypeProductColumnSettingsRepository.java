package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserTypeProductColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserTypeProductColumnSettingsRepository extends JpaRepository<UserTypeProductColumnSettings, Long> {
    Optional<UserTypeProductColumnSettings> findByUserId(Integer userId);
}
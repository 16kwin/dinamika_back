package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserDashboardSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserDashboardSettingsRepository extends JpaRepository<UserDashboardSettings, Long> {
    Optional<UserDashboardSettings> findByUserId(Integer userId);
}

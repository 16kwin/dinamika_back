package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserTransferColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserTransferColumnSettingsRepository extends JpaRepository<UserTransferColumnSettings, Long> {
    Optional<UserTransferColumnSettings> findByUserId(Integer userId);
}
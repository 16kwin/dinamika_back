package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserSupplierBrandColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSupplierBrandColumnSettingsRepository extends JpaRepository<UserSupplierBrandColumnSettings, Long> {
    Optional<UserSupplierBrandColumnSettings> findByUserId(Integer userId);
}
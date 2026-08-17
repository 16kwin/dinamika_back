// UserEnterpriseColumnSettingsRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserEnterpriseColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserEnterpriseColumnSettingsRepository extends JpaRepository<UserEnterpriseColumnSettings, Long> {
    Optional<UserEnterpriseColumnSettings> findByUserId(Integer userId);
}
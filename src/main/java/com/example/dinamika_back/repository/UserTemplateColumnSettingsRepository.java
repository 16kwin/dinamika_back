// UserTemplateColumnSettingsRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserTemplateColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserTemplateColumnSettingsRepository extends JpaRepository<UserTemplateColumnSettings, Long> {

    Optional<UserTemplateColumnSettings> findByUserId(Integer userId);
}
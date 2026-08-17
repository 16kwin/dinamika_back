// UserSectionColumnSettingsRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserSectionColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSectionColumnSettingsRepository extends JpaRepository<UserSectionColumnSettings, Long> {
    Optional<UserSectionColumnSettings> findByUserId(Integer userId);
}
// UserEmployeeColumnSettingsRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserEmployeeColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserEmployeeColumnSettingsRepository extends JpaRepository<UserEmployeeColumnSettings, Long> {

    Optional<UserEmployeeColumnSettings> findByUserId(Integer userId);
}
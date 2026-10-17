// UserMoveOrderColumnSettingsRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserMoveOrderColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserMoveOrderColumnSettingsRepository extends JpaRepository<UserMoveOrderColumnSettings, Long> {

    Optional<UserMoveOrderColumnSettings> findByUserId(Integer userId);
}
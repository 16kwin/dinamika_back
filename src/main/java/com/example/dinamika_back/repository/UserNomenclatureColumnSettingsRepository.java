// UserNomenclatureColumnSettingsRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserNomenclatureColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserNomenclatureColumnSettingsRepository extends JpaRepository<UserNomenclatureColumnSettings, Long> {
    Optional<UserNomenclatureColumnSettings> findByUserId(Integer userId);
}
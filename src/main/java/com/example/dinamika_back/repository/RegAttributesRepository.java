// ==================== НОВЫЙ ФАЙЛ: RegAttributesRepository.java ====================
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegAttributes;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RegAttributesRepository extends JpaRepository<RegAttributes, UUID> {
    List<RegAttributes> findByMaterialUid(UUID materialUid);
    void deleteByMaterialUid(UUID materialUid);
}
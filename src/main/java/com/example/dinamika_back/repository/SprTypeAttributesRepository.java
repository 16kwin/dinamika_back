// ==================== НОВЫЙ ФАЙЛ: SprTypeAttributesRepository.java ====================
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprTypeAttributes;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SprTypeAttributesRepository extends JpaRepository<SprTypeAttributes, UUID> {
}
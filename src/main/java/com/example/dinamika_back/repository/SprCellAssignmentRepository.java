// SprCellAssignmentRepository.java — ПОЛНЫЙ ФАЙЛ
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprCellAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SprCellAssignmentRepository extends JpaRepository<SprCellAssignment, UUID> {

    /** Все назначения, у которых тип учёта входит в переданный набор */
    List<SprCellAssignment> findByTypeUidIn(Collection<UUID> typeUids);

    /** Все назначения с типом учёта по имени (например, 'ТМЦ', 'Готовая деталь') */
    List<SprCellAssignment> findByTypeTypeNameIn(Collection<String> typeNames);
}
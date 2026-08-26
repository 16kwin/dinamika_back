package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.TypeMaterialEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TypeMaterialEventLogRepository extends JpaRepository<TypeMaterialEventLog, UUID> {
    List<TypeMaterialEventLog> findByTypeMaterialUidOrderByCreatedAtDesc(UUID typeMaterialUid);
    List<TypeMaterialEventLog> findAllByOrderByCreatedAtDesc();
}
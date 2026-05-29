// RegGroupMaterialRepository.java (обновлённый)
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegGroupMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegGroupMaterialRepository extends JpaRepository<RegGroupMaterial, UUID> {

    List<RegGroupMaterial> findByParentGroup(UUID parentGroupId);
    List<RegGroupMaterial> findByParentGroupIsNull();
    
    Optional<RegGroupMaterial> findByGroupCode(Integer groupCode);
    
    @Query("SELECT COALESCE(MAX(g.groupCode), 0) FROM RegGroupMaterial g")
    Integer findMaxGroupCode();
}
// TemplateCategoryRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.TemplateCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TemplateCategoryRepository extends JpaRepository<TemplateCategory, Long> {

    Optional<TemplateCategory> findByUid(UUID uid);

    Optional<TemplateCategory> findByName(String name);

    List<TemplateCategory> findByParentCategoryIsNull();

    List<TemplateCategory> findByParentCategoryId(Long parentId);

    List<TemplateCategory> findByParentCategoryUid(UUID parentUid);

    boolean existsByNameAndParentCategoryId(String name, Long parentCategoryId);

    boolean existsByNameAndParentCategoryIsNull(String name);

    @Query("SELECT COALESCE(MAX(c.code), 0) FROM TemplateCategory c")
    Integer findMaxCode();
}
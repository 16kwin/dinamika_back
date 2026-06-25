package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.TemplateCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TemplateCategoryRepository extends JpaRepository<TemplateCategory, Long> {
    Optional<TemplateCategory> findByUid(java.util.UUID uid);
    Optional<TemplateCategory> findByName(String name);
}
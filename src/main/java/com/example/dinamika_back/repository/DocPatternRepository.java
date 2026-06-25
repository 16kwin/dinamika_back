package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DocPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocPatternRepository extends JpaRepository<DocPattern, UUID> {

    /** Найти максимальный номер шаблона */
    @Query("SELECT COALESCE(MAX(d.number), 0) FROM DocPattern d")
    Long findMaxNumber();

    /** Найти все шаблоны по категории */
    List<DocPattern> findByCategoryId(Long categoryId);

    /** Найти шаблоны по категории, упорядоченные по номеру */
    List<DocPattern> findByCategoryIdOrderByNumberAsc(Long categoryId);

    /** Найти все шаблоны, упорядоченные по номеру */
    List<DocPattern> findAllByOrderByNumberAsc();
}
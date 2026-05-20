package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegAttached;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Репозиторий для работы с регистром прикреплённых файлов */
@Repository
public interface RegAttachedRepository extends JpaRepository<RegAttached, UUID> {
    
    /** Найти все файлы, прикреплённые к конкретному материалу */
    List<RegAttached> findByMaterialUid(UUID materialUid);
}
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DashGraphGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DashGraphGroupRepository extends JpaRepository<DashGraphGroup, String> {

    /** Группы номенклатуры в порядке легенды */
    List<DashGraphGroup> findAllByOrderBySortOrderAsc();
}

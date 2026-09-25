package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DashGraphNomenclature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DashGraphNomenclatureRepository extends JpaRepository<DashGraphNomenclature, String> {

    /** Номенклатура графа в порядке справочника */
    List<DashGraphNomenclature> findAllByOrderBySortOrderAsc();
}

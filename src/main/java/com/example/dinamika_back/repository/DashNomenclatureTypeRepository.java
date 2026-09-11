package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DashNomenclatureType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DashNomenclatureTypeRepository extends JpaRepository<DashNomenclatureType, String> {

    /** Виды в порядке макета */
    List<DashNomenclatureType> findAllByOrderBySortOrderAsc();
}

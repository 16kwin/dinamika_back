// StationModelRepository.java — ПОЛНЫЙ ФАЙЛ (добавлены findByTypeUid и findByManufacturerUid)
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.StationModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StationModelRepository extends JpaRepository<StationModel, UUID> {

    @Query("SELECT COALESCE(MAX(m.code), 0) FROM StationModel m")
    Integer findMaxCode();

    Optional<StationModel> findByName(String name);
    boolean existsByName(String name);
    boolean existsByCode(Integer code);

    @Query("SELECT m FROM StationModel m WHERE m.type.uid = :typeUid")
    List<StationModel> findByTypeUid(@Param("typeUid") UUID typeUid);

    @Query("SELECT m FROM StationModel m WHERE m.manufacturer.uid = :manufacturerUid")
    List<StationModel> findByManufacturerUid(@Param("manufacturerUid") UUID manufacturerUid);
}
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.StationModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StationModelRepository extends JpaRepository<StationModel, Integer> {
    Optional<StationModel> findByModelNumber(Integer modelNumber);
    boolean existsByModelNumber(Integer modelNumber);
}
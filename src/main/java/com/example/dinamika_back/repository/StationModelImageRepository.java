// StationModelImageRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.StationModelImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StationModelImageRepository extends JpaRepository<StationModelImage, UUID> {
    List<StationModelImage> findByModelUidOrderBySortOrderAsc(UUID modelUid);
    void deleteByModelUid(UUID modelUid);
}
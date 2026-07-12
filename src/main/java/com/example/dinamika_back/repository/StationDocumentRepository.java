package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.StationDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StationDocumentRepository extends JpaRepository<StationDocument, UUID> {
    List<StationDocument> findByStationUidOrderByCreatedAtDesc(String stationUid);
    void deleteByStationUid(String stationUid);
}
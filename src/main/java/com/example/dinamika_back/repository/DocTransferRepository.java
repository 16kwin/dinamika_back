package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DocTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocTransferRepository extends JpaRepository<DocTransfer, UUID> {

    List<DocTransfer> findByDocBaseUid(UUID docBaseUid);

    List<DocTransfer> findByStatus(String status);

    @Query("SELECT MAX(d.code) FROM DocTransfer d")
    Integer findMaxCode();
}
// RegMoveOrderLineRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegMoveOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RegMoveOrderLineRepository extends JpaRepository<RegMoveOrderLine, UUID> {

    List<RegMoveOrderLine> findByDocUid(UUID docUid);

    List<RegMoveOrderLine> findByDocUidOrderByCreatedAtAsc(UUID docUid);

    List<RegMoveOrderLine> findByTransferUid(UUID transferUid);
}
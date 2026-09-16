package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegStockLevelControlBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RegStockLevelControlBindingRepository extends JpaRepository<RegStockLevelControlBinding, UUID> {

    @Query("SELECT b FROM RegStockLevelControlBinding b " +
           "LEFT JOIN FETCH b.material m " +
           "WHERE b.doc.uid = :docUid " +
           "ORDER BY b.createdAt ASC")
    List<RegStockLevelControlBinding> findByDocUid(UUID docUid);

    @Modifying
    @Query("DELETE FROM RegStockLevelControlBinding b WHERE b.doc.uid = :docUid")
    void deleteByDocUid(UUID docUid);
}
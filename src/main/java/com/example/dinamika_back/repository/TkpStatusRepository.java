// AWMS — repository/TkpStatusRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.TkpStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TkpStatusRepository extends JpaRepository<TkpStatus, Long> {
    List<TkpStatus> findByTkpUidOrderByDatetimeDesc(String tkpUid);
}
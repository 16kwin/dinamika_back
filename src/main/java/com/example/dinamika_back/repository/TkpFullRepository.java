// AWMS — repository/TkpFullRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.TkpFull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TkpFullRepository extends JpaRepository<TkpFull, String> {
    Optional<TkpFull> findByTkpUid(String tkpUid);
}
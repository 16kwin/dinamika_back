// AWMS — repository/TkpListRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.TkpList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TkpListRepository extends JpaRepository<TkpList, String> {
    List<TkpList> findByStatus(String status);
}
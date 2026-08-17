// CountryEventLogRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.CountryEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CountryEventLogRepository extends JpaRepository<CountryEventLog, UUID> {
    List<CountryEventLog> findByCountryUidOrderByCreatedAtDesc(UUID countryUid);
    List<CountryEventLog> findAllByOrderByCreatedAtDesc();
}
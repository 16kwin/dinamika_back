// LocationEventLogRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.LocationEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LocationEventLogRepository extends JpaRepository<LocationEventLog, UUID> {
    List<LocationEventLog> findByLocationUidOrderByCreatedAtDesc(UUID locationUid);
    List<LocationEventLog> findAllByOrderByCreatedAtDesc();
}
// EmployeeEventLogRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.EmployeeEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmployeeEventLogRepository extends JpaRepository<EmployeeEventLog, UUID> {

    List<EmployeeEventLog> findByEmployeeUidOrderByCreatedAtDesc(UUID employeeUid);

    List<EmployeeEventLog> findAllByOrderByCreatedAtDesc();
}
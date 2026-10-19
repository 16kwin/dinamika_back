// EmployeeRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
}
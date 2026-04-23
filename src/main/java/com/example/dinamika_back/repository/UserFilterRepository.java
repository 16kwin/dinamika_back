// UserFilterRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserFilterRepository extends JpaRepository<UserFilter, Long> {
    
    Optional<UserFilter> findByUserId(Integer userId);
    
    void deleteByUserId(Integer userId);
}
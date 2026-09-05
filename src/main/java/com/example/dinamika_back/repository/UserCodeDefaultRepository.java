package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.UserCodeDefault;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserCodeDefaultRepository extends JpaRepository<UserCodeDefault, Long> {
    Optional<UserCodeDefault> findByUserIdAndCodeKind(Integer userId, String codeKind);
}
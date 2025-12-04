package com.example.dinamika_back.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.dinamika_back.model.RefreshTokenDbEntity;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenDbEntity, UUID> {

    void deleteByToken(String token);

    Optional<RefreshTokenDbEntity> findByToken(String token);

    void deleteByUserId(long userId);

}

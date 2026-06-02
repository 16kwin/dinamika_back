package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprMaterialQrcode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SprMaterialQrcodeRepository extends JpaRepository<SprMaterialQrcode, UUID> {
    List<SprMaterialQrcode> findByMaterialUid(UUID materialUid);
    void deleteByMaterialUid(UUID materialUid);
}
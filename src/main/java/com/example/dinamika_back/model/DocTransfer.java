package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "doc_transfer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocTransfer {

    @Id
    @GeneratedValue
    @Column(name = "uid", nullable = false, updatable = false)
    private UUID uid;

    @Column(name = "code")
    private Integer code;

    @Column(name = "doc_datetime", nullable = false)
    private LocalDateTime docDatetime;

    @Column(name = "doc_base_uid")
    private UUID docBaseUid;

    @Column(name = "doc_base_type", length = 50)
    private String docBaseType;

    @Column(name = "stock_from_uid", length = 50)
    private String stockFromUid;

    @Column(name = "stock_to_uid", length = 50)
    private String stockToUid;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (docDatetime == null) docDatetime = now;
        if (status == null) status = "DRAFT";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
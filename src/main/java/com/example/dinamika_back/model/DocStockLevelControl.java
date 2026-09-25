package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "doc_stock_level_control")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocStockLevelControl {

    @Id
    @Column(name = "uid", columnDefinition = "uuid")
    private UUID uid;

    @Column(name = "code")
    private Integer code;

    @Column(name = "doc_date")
    private LocalDate docDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_uid", referencedColumnName = "uid")
    private Station station;

    @Column(name = "is_posted", nullable = false)
    private Boolean isPosted;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (uid == null) uid = UUID.randomUUID();
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isPosted == null) isPosted = false;
        if (docDate == null) docDate = LocalDate.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
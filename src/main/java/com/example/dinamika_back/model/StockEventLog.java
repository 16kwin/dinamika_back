package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_event_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockEventLog {

    @Id
    @GeneratedValue
    @Column(name = "uid", nullable = false, updatable = false)
    private UUID uid;

    @Column(name = "balance_uid")
    private UUID balanceUid;

    @Column(name = "reservation_uid")
    private UUID reservationUid;

    @Column(name = "material_uid")
    private UUID materialUid;

    @Column(name = "doc_refill_uid")
    private UUID docRefillUid;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "event_description", nullable = false, columnDefinition = "TEXT")
    private String eventDescription;

    @Column(name = "field_name", length = 255)
    private String fieldName;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "author", length = 255)
    private String author;

    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (source == null) source = "Через карточку";
    }
}
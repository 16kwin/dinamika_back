package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refill_event_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefillEventLog {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @Column(name = "doc_refill_uid")
    private UUID docRefillUid;

    @Column(name = "cell_uid")
    private UUID cellUid;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "event_description", nullable = false, columnDefinition = "TEXT")
    private String eventDescription;

    @Column(name = "field_name")
    private String fieldName;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "author")
    private String author;

    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (uid == null) uid = UUID.randomUUID();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (source == null) source = "Через карточку";
    }
}
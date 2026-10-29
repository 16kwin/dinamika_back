package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transfer_event_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransferEventLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uid;

    @Column(name = "doc_uid")
    private UUID docUid;

    @Column(name = "line_uid")
    private UUID lineUid;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "event_description")
    private String eventDescription;

    @Column(name = "field_name")
    private String fieldName;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    private String author;
    private String source;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
// DocMoveOrder.java
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "doc_move_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocMoveOrder {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @Column(name = "code", unique = true)
    private Integer code;

    @Column(name = "doc_datetime", nullable = false)
    private LocalDateTime docDatetime;

    @Column(name = "stock_to_uid")
    private String stockToUid;

    @Column(name = "deadline")
    private LocalDateTime deadline;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
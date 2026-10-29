// DocTransfer.java — ПОЛНЫЙ ФАЙЛ (V25: priority, due_date, operator_uid, agreed, cancelled)
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
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @Column(name = "code")
    private Integer code;

    @Column(name = "doc_datetime", nullable = false)
    private LocalDateTime docDatetime;

    @Column(name = "doc_base_uid")
    private UUID docBaseUid;

    @Column(name = "doc_base_type")
    private String docBaseType;

    @Column(name = "stock_from_uid")
    private String stockFromUid;

    @Column(name = "stock_to_uid")
    private String stockToUid;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "priority")
    private String priority;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "operator_uid")
    private UUID operatorUid;

    @Column(name = "agreed")
    private Boolean agreed;

    @Column(name = "cancelled")
    private Boolean cancelled;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

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
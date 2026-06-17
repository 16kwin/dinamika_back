// RegSupplierEventLog.java
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reg_supplier_event_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegSupplierEventLog {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_uid", nullable = false)
    private SprSupplier supplier;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "event_description", columnDefinition = "text")
    private String eventDescription;

    @Column(name = "field_name")
    private String fieldName;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(name = "author")
    private String author;

    @Column(name = "source")
    private String source;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reg_supplier_integration")
@Getter
@Setter
@NoArgsConstructor
public class RegSupplierIntegration {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @ManyToOne
    @JoinColumn(name = "supplier_uid", nullable = false)
    private SprSupplier supplier;

    @Column(name = "event")
    private String event;

    @Column(name = "exchange_type")
    private String exchangeType;

    @Column(name = "direction")
    private String direction;

    @Column(name = "protocol")
    private String protocol;

    @Column(name = "target_system")
    private String targetSystem;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
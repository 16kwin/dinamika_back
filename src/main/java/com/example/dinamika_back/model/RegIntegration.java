// RegIntegration.java
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reg_integration")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegIntegration {

    @Id
    private UUID uid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_uid", nullable = false)
    private SprMaterial material;

    @Column(name = "event", nullable = false)
    private String event;

    @Column(name = "exchange_type", nullable = false)
    private String exchangeType;

    @Column(name = "direction", nullable = false)
    private String direction;

    @Column(name = "protocol", nullable = false)
    private String protocol;

    @Column(name = "target_system", nullable = false)
    private String targetSystem;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
// Station.java — ПОЛНЫЙ ФАЙЛ
package com.example.dinamika_back.model;

import com.example.dinamika_back.listener.StationEntityListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stations")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(StationEntityListener.class)
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uid", unique = true, nullable = false, length = 50)
    private String uid;

    @Column(name = "code", unique = true)
    private Integer code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "production_date")
    private LocalDate productionDate;

    @Column(name = "serial_number", length = 255)
    private String serialNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "holding_id")
    private Holding holding;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id")
    private Enterprise enterprise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workshop_id")
    private Workshop workshop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private Section section;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StationStatus status;

    @Column(name = "total_cells", nullable = false)
    private Integer totalCells;

    @Column(name = "filled_cells", nullable = false)
    private Integer filledCells;

    @Column(name = "template_nomenclature_count", nullable = false)
    private Integer templateNomenclatureCount;

    @Column(name = "remaining_nomenclature_count", nullable = false)
    private Integer remainingNomenclatureCount;

    @Column(name = "max_ready_parts", nullable = false)
    private Integer maxReadyParts;

    @Column(name = "ready_parts_count", nullable = false)
    private Integer readyPartsCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id", referencedColumnName = "uid")
    private StationModel model;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "configuration_uid", referencedColumnName = "uid")
    private StationConfiguration configuration;

    @Column(name = "parent_uid", length = 50)
    private String parentUid;

    @Column(name = "has_error", nullable = false)
    private Boolean hasError;

    @Column(name = "is_tmc", nullable = false)
    private Boolean isTmc;

    @Column(name = "is_sgd", nullable = false)
    private Boolean isSgd;

    @Column(name = "is_ok", nullable = false)
    private Boolean isOk;

    @Column(name = "is_additional_module", nullable = false)
    private Boolean isAdditionalModule;

    @Column(name = "has_additional_module", nullable = false)
    private Boolean hasAdditionalModule;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "network_port")
    private Integer networkPort;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_template_uid", referencedColumnName = "uid")
    private DocPattern activeTemplate;

    @Column(name = "created_at", updatable = false)
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
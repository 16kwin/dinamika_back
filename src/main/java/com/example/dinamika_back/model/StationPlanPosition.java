package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/**
 * Позиция станции на плане цеха (station_plan_positions): точка «пола» станции в долях 0..1 от плана.
 *
 * <p>Ключ составной и задаётся сразу, поэтому позиция реализует {@link Persistable}: при полной замене
 * расстановки новые записи вставляются без SELECT на каждую (как было бы при merge).
 */
@Entity
@Table(name = "station_plan_positions")
@IdClass(StationPlanPositionId.class)
@Getter
@Setter
@NoArgsConstructor
public class StationPlanPosition implements Persistable<StationPlanPositionId> {

    @Id
    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Id
    @Column(name = "station_id", nullable = false)
    private Long stationId;

    @Column(name = "x", nullable = false)
    private Double x;

    @Column(name = "y", nullable = false)
    private Double y;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Позиция создана в этом запросе и ещё не записана в базу */
    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean fresh = true;

    public StationPlanPosition(Long planId, Long stationId, double x, double y) {
        this.planId = planId;
        this.stationId = stationId;
        this.x = x;
        this.y = y;
    }

    @Override
    public StationPlanPositionId getId() {
        return new StationPlanPositionId(planId, stationId);
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    @PostLoad
    @PostPersist
    void markStored() {
        fresh = false;
    }
}

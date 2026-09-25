package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Панель аудитора: операций за день, из них инцидентов и выдач сверх нормы (dash_audit_daily).
 */
@Entity
@Table(name = "dash_audit_daily")
@Getter
@Setter
@NoArgsConstructor
public class DashAuditDaily {

    @Id
    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "operations", nullable = false)
    private Integer operations;

    @Column(name = "incidents", nullable = false)
    private Integer incidents;

    @Column(name = "over_norm", nullable = false)
    private Integer overNorm;
}

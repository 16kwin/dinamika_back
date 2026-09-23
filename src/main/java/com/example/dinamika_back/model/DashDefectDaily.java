package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Факт выпуска и брака за день в разрезе субъект × элемент раскрытия (dash_defect_daily).
 */
@Entity
@Table(name = "dash_defect_daily")
@Getter
@Setter
@NoArgsConstructor
public class DashDefectDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "view_kind", nullable = false, length = 16)
    private String viewKind;

    @Column(name = "subject_key", nullable = false, length = 64)
    private String subjectKey;

    @Column(name = "item_key", nullable = false, length = 64)
    private String itemKey;

    @Column(name = "released_qty", nullable = false, precision = 14, scale = 4)
    private BigDecimal releasedQty;

    @Column(name = "defect_qty", nullable = false, precision = 14, scale = 4)
    private BigDecimal defectQty;
}

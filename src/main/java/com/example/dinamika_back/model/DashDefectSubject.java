package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Субъект графика «Уровень брака»: деталь (view_kind='part') или подразделение (view_kind='workshop').
 */
@Entity
@Table(name = "dash_defect_subject")
@IdClass(DashDefectSubjectId.class)
@Getter
@Setter
@NoArgsConstructor
public class DashDefectSubject {

    @Id
    @Column(name = "view_kind", nullable = false, length = 16)
    private String viewKind;

    @Id
    @Column(name = "subject_key", nullable = false, length = 64)
    private String subjectKey;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}

package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Настройки панели «Экономический блок» на пользователя: выбранные виды номенклатуры
 * для столбчатой диаграммы и радара (JSON-массивы ключей, порядок важен).
 */
@Entity
@Table(name = "user_dashboard_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDashboardSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Integer userId;

    /** Виды для столбчатой диаграммы «Затраты по видам номенклатуры» */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bar_types_json", columnDefinition = "jsonb", nullable = false)
    private String barTypesJson;

    /** Виды (оси) для радара «Распределение затрат» */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "radar_types_json", columnDefinition = "jsonb", nullable = false)
    private String radarTypesJson;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
        updatedAt = java.time.LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.LocalDateTime.now();
    }
}

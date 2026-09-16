package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_stock_level_control_column_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStockLevelControlColumnSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Integer userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "columns_json", columnDefinition = "jsonb")
    private String columnsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filters_json", columnDefinition = "jsonb")
    private String filtersJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sort_json", columnDefinition = "jsonb")
    private String sortJson;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (columnsJson == null) columnsJson = "{}";
        if (filtersJson == null) filtersJson = "{}";
        if (sortJson == null) sortJson = "{}";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
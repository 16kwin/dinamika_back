// UserNomenclatureColumnSettings.java
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_nomenclature_column_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNomenclatureColumnSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Integer userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "columns_json", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private String columnsJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filters_json", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private String filtersJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sort_json", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private String sortJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_path_json", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private String currentPathJson = "[]";

    @Column(name = "created_at")
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
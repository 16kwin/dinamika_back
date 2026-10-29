package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_transfer_column_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserTransferColumnSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "columns_json", columnDefinition = "jsonb")
    private String columnsJson;

    @Column(name = "filters_json", columnDefinition = "jsonb")
    private String filtersJson;

    @Column(name = "sort_json", columnDefinition = "jsonb")
    private String sortJson;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
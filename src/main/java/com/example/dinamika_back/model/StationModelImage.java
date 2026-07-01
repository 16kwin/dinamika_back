// StationModelImage.java
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "station_model_images")
@Getter
@Setter
@NoArgsConstructor
public class StationModelImage {

    @Id
    @Column(name = "uid", updatable = false, nullable = false)
    private UUID uid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_uid", nullable = false)
    private StationModel model;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "original_name", length = 500)
    private String originalName;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (uid == null) uid = UUID.randomUUID();
        createdAt = LocalDateTime.now();
    }
}
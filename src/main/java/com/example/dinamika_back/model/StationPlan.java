package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * План цеха для интерактивной карты станций (station_plans) — одна запись на цех.
 * Поля image* пусты, если картинка не загружена: тогда карта рисует автоматическую схему цеха.
 */
@Entity
@Table(name = "station_plans")
@Getter
@Setter
@NoArgsConstructor
public class StationPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "workshop_id", nullable = false, unique = true)
    private Long workshopId;

    /** Путь относительно каталога планов: {workshopId}/{uuid}.{ext} */
    @Column(name = "image_path", length = 500)
    private String imagePath;

    @Column(name = "image_mime", length = 50)
    private String imageMime;

    @Column(name = "image_width")
    private Integer imageWidth;

    @Column(name = "image_height")
    private Integer imageHeight;

    /** Версия плана: растёт при каждом сохранении расстановки и смене картинки */
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean hasImage() {
        return imagePath != null;
    }

    @PrePersist
    void onCreate() {
        if (version == null) version = 0L;
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

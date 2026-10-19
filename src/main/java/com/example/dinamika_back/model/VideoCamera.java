package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Камера станции для видеоархива (video_camera).
 * Строка подключения хранится целиком, вместе с логином и паролем, как её вводят на странице.
 */
@Entity
@Table(name = "video_camera")
@Getter
@Setter
@NoArgsConstructor
public class VideoCamera {

    /** Камера станции — снимает ячейки и окно выдачи */
    public static final String TYPE_STATION = "station";
    /** Обзорная камера — снимает зону перед станцией */
    public static final String TYPE_OVERVIEW = "overview";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private UUID uid;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "ip", nullable = false, length = 64)
    private String ip;

    @Column(name = "mac", length = 32)
    private String mac;

    @Column(name = "camera_type", nullable = false, length = 16)
    private String cameraType;

    @Column(name = "rtsp_url", nullable = false, length = 512)
    private String rtspUrl;

    @Column(name = "station_id")
    private Long stationId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (uid == null) uid = UUID.randomUUID();
        if (sortOrder == null) sortOrder = 0;
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

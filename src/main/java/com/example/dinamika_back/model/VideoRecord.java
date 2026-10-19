package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Запись видеоархива по событию станции (video_record) — одна строка журнала.
 * Файлы камер, снимавших событие, — в {@link VideoRecordFile}.
 */
@Entity
@Table(name = "video_record")
@Getter
@Setter
@NoArgsConstructor
public class VideoRecord {

    public static final String STATUS_RECORDING = "recording";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_FAILED = "failed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Идентификатор записи — входит в имя файла */
    @Column(name = "uid", nullable = false, unique = true)
    private UUID uid;

    /** Код события, см. {@link com.example.dinamika_back.service.VideoEventType} */
    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "station_id")
    private Long stationId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "duration_seconds", precision = 10, scale = 2)
    private BigDecimal durationSeconds;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (uid == null) uid = UUID.randomUUID();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

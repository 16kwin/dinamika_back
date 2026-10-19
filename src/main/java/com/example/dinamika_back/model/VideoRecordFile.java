package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Файл записи одной камеры по событию (video_record_file).
 * Сведения о камере скопированы на момент записи: журнал не меняется, если камеру переименуют.
 */
@Entity
@Table(name = "video_record_file")
@Getter
@Setter
@NoArgsConstructor
public class VideoRecordFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(name = "camera_id")
    private Long cameraId;

    @Column(name = "camera_name", nullable = false, length = 64)
    private String cameraName;

    @Column(name = "camera_ip", nullable = false, length = 64)
    private String cameraIp;

    @Column(name = "camera_type", nullable = false, length = 16)
    private String cameraType;

    /** Путь относительно каталога видеоархива */
    @Column(name = "file_path", nullable = false, length = 1024)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "duration_seconds", precision = 10, scale = 2)
    private BigDecimal durationSeconds;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}

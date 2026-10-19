package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Файл записи одной камеры. Ссылки ведут на отдачу файла с поддержкой перемотки. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoRecordFileDTO {
    private Long id;
    private Long cameraId;
    private String cameraName;
    private String cameraIp;
    /** 'station' или 'overview' */
    private String cameraType;
    /** «Камера станции» или «Обзорная камера» — подпись над видео */
    private String cameraTitle;
    private String fileName;
    private Long fileSize;
    private BigDecimal durationSeconds;
    /** 'recording', 'done' или 'failed' */
    private String status;
    private String errorMessage;
    /** Путь для видеоплеера, относительно адреса API */
    private String contentUrl;
    /** Путь для скачивания, относительно адреса API */
    private String downloadUrl;
}

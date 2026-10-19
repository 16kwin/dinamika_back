package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Запись с живого потока по кнопке «Записать» — файл для скачивания на компьютер зрителя. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoClipDTO {
    private String id;
    private Long cameraId;
    private String cameraName;
    private LocalDateTime startedAt;
    /** Заполняются после остановки */
    private String fileName;
    private Long fileSize;
    private BigDecimal durationSeconds;
    private String downloadUrl;
}

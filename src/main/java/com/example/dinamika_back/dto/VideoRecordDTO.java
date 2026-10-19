package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Строка журнала видеоархива: событие, станция, период и файлы камер. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoRecordDTO {
    private Long id;
    /** Номер видеозаписи для заголовка «Видеозапись № 001» */
    private Long number;
    private UUID uid;
    /** Код события: 'authority', 'issue' */
    private String eventType;
    /** «Авторизация», «Выдача» */
    private String eventTitle;
    private Long stationId;
    private String stationName;
    /** 'recording', 'done' или 'failed' */
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private BigDecimal durationSeconds;
    private String errorMessage;
    private List<VideoRecordFileDTO> files;
}

package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Состояние живого потока камеры: сколько зрителей и какая частота кадров приходит с камеры. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoLiveStatusDTO {
    private Long cameraId;
    private String cameraName;
    /** Строка кодека для MediaSource, например hev1.1.6.L120.90 */
    private String codec;
    /** Зрители и записи по кнопке «Записать» */
    private int viewers;
    /** Кадров в секунду с камеры за последние 5 секунд */
    private double fps;
    private double bitrateMbps;
    /** Кадров, пропущенных медленными зрителями */
    private long droppedFrames;
    private LocalDateTime startedAt;
}

package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Результат проверки потока камеры — кнопка «Поток». */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoStreamCheckDTO {
    private Long cameraId;
    private boolean ok;
    /** Понятное описание результата или причины ошибки */
    private String message;
    private String codec;
    private Integer width;
    private Integer height;
    private Double fps;
    /** Сколько заняло подключение, мс */
    private long elapsedMs;
}

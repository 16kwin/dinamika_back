package com.example.dinamika_back.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Тело PUT /api/video/cameras/{id}: поля, которые правятся на странице. Пустое поле не меняется. */
@Data
@NoArgsConstructor
public class VideoCameraUpdateDTO {
    private String name;
    private String ip;
    private String mac;
    /** 'station' или 'overview' */
    private String cameraType;
    private String rtspUrl;
    private Long stationId;
}

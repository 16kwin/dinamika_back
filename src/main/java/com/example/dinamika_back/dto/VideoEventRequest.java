package com.example.dinamika_back.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Тело POST /api/video/events — событие станции, которое запускает или останавливает запись.
 * Пример: { "stationId": 1, "eventType": "authority", "phase": "start" }.
 */
@Data
@NoArgsConstructor
public class VideoEventRequest {
    private Long stationId;
    /** 'authority' — авторизация, 'issue' — выдача */
    private String eventType;
    /** 'start' — начало события, 'stop' — окончание */
    private String phase;
}

package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Камера видеоархива для страницы «Журнал событий: видеоархив». */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoCameraDTO {
    private Long id;
    private UUID uid;
    private String name;
    private String ip;
    private String mac;
    /** 'station' или 'overview' */
    private String cameraType;
    /** «Станция» или «Обзорная» — как в поле «Тип камеры» */
    private String cameraTypeTitle;
    /** Строка подключения; пароль заменён на «***» — настоящий пароль в браузер не уходит */
    private String rtspUrl;
    private Long stationId;
    /** Код станции, например ST-001 — по нему окно «Видео» на панели станций находит камеры */
    private String stationUid;
    private String stationName;
    /** Идёт ли сейчас запись с этой камеры */
    private boolean recording;
}

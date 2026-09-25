package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Карточка «Экрана событий текущего дня». */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashDayEventDTO {
    private Long id;
    private String title;
    private LocalDateTime at;
    private String person;
    /** Текст плашки статуса («В работе», «Контроль пройден» и т.п.) */
    private String status;
    /** Цвет плашки: progress | success | danger */
    private String tone;
}

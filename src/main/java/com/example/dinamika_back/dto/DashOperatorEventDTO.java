package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Строка ленты «Заказы на поставку» или «Экран событий». */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashOperatorEventDTO {
    private Long id;
    private String title;
    private LocalDateTime at;
    /** true — вкладка «Завершено», false — «В работе» */
    private Boolean done;
}

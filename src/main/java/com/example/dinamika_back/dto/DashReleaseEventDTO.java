package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Строка ленты «Выпуск продукции». */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashReleaseEventDTO {
    private Long id;
    private String name;
    private LocalDateTime at;
}

package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Строка ленты контроля качества. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashControlEventDTO {
    private Long id;
    private String title;
    private LocalDateTime at;
    private String department;
    private String executor;
    private String controller;
    /** pending — «На контроль», passed — «Контроль пройден», failed — «Контроль не пройден» */
    private String status;
}

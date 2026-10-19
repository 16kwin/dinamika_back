package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Картинка плана цеха. В url есть ?v=метка файла: она меняется только при замене картинки, поэтому
 * после сохранения расстановки адрес прежний и клиенты не перекачивают картинку
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StationPlanImageDTO {
    private String url;
    private Integer width;
    private Integer height;
    private String mime;
    private LocalDateTime updatedAt;
}

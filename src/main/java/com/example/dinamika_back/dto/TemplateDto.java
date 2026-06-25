package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateDto {
    private UUID uid;
    private String name;
    private Long number;
    private Long categoryId;
    private String categoryName;
    private String configuration;
    private Integer totalCells;
    private Integer filledCells;
    private Integer freeCells;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean active;
    private List<String> stationNames;
}
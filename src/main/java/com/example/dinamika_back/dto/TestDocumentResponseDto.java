package com.example.dinamika_back.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TestDocumentResponseDto {
    private Long id;
    private Long userId;
    private String title;
    private String field2;
    private String field3;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean completed;
}
// dto/TemplateCategoryDto.java
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateCategoryDto {
    private Long id;
    private UUID uid;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
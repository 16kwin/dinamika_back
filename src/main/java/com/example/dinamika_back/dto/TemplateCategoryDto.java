// TemplateCategoryDto.java
package com.example.dinamika_back.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class TemplateCategoryDto {
    private Long id;
    private UUID uid;
    private String name;
    private Integer code;

    private Long parentCategoryId;
    private UUID parentCategoryUid;
    private String parentCategoryName;

    private List<TemplateCategoryDto> children;
    private List<TemplateDto> templates;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
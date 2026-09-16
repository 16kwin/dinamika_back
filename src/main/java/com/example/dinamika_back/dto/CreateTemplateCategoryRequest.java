// CreateTemplateCategoryRequest.java
package com.example.dinamika_back.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CreateTemplateCategoryRequest {
    private String name;
    private UUID parentCategoryUid;
}
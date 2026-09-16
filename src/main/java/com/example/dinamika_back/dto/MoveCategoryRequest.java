// MoveCategoryRequest.java
package com.example.dinamika_back.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class MoveCategoryRequest {
    private UUID categoryUid;
    private UUID newParentUid;
}
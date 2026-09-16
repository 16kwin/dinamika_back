// MoveTemplateRequest.java
package com.example.dinamika_back.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class MoveTemplateRequest {
    private UUID templateUid;
    private UUID newCategoryUid;
}
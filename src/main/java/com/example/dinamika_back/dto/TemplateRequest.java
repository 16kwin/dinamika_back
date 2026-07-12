// TemplateRequest.java — ПОЛНЫЙ ФАЙЛ
package com.example.dinamika_back.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class TemplateRequest {
    private String name;
    private Long categoryId;
    private String configuration;
    private UUID configurationUid;
}
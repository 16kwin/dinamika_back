// dto/TemplateRequest.java
package com.example.dinamika_back.dto;

import lombok.Data;

@Data
public class TemplateRequest {
    private String name;
    private Long categoryId;
    private String configuration;
}
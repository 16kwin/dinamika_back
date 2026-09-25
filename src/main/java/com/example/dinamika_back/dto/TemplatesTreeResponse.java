// TemplatesTreeResponse.java
package com.example.dinamika_back.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class TemplatesTreeResponse {
    private List<TemplateCategoryDto> tree;
    private List<String> columns;
    private Map<String, Double> columnWidths;
    private List<String> requiredColumns;
    private String columnsJson;
    private String filtersJson;
    private String sortJson;
    private String currentPathJson;
}
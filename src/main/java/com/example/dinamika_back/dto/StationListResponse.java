// StationListResponse.java — ПОЛНЫЙ ФАЙЛ (с requiredColumns)
package com.example.dinamika_back.dto;

import lombok.*;
import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StationListResponse {
    private List<String> columns;
    private List<Map<String, Object>> data;
    private Map<String, Double> columnWidths;
    private List<String> requiredColumns;
    
    public StationListResponse(List<String> columns, List<Map<String, Object>> data) {
        this.columns = columns;
        this.data = data;
        this.columnWidths = new HashMap<>();
        this.requiredColumns = new ArrayList<>();
    }
    
    public StationListResponse(List<String> columns, List<Map<String, Object>> data, Map<String, Double> columnWidths) {
        this.columns = columns;
        this.data = data;
        this.columnWidths = columnWidths;
        this.requiredColumns = new ArrayList<>();
    }
}
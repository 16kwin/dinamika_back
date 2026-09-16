package com.example.dinamika_back.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockLevelControlListResponse {
    private List<String> columns;
    private List<Map<String, Object>> data;
    private Map<String, Double> columnWidths;
    private List<String> requiredColumns;
}
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManufacturerListResponse {
    private List<String> columns;
    private List<Map<String, Object>> data;
    private Map<String, Double> columnWidths;
    private List<String> requiredColumns;
}
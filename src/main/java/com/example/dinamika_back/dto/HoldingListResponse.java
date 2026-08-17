package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldingListResponse {
    private List<String> columns;
    private List<HoldingFlatDto> data;
    private Map<String, Double> columnWidths;
    private List<String> requiredColumns;
}
// EmployeeListResponse.java
package com.example.dinamika_back.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeListResponse {
    private List<String> columns;
    private List<EmployeeFlatDto> data;
    private Map<String, Double> columnWidths;
    private List<String> requiredColumns;
}
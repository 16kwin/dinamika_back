// StationConfigurationListResponse.java — НОВЫЙ DTO
package com.example.dinamika_back.dto;

import java.util.List;
import java.util.Map;

public class StationConfigurationListResponse {
    private List<String> columns;
    private List<StationConfigurationDto> data;
    private Map<String, Double> columnWidths;
    private List<String> requiredColumns;

    public StationConfigurationListResponse() {}

    public StationConfigurationListResponse(List<String> columns, List<StationConfigurationDto> data,
                                            Map<String, Double> columnWidths, List<String> requiredColumns) {
        this.columns = columns;
        this.data = data;
        this.columnWidths = columnWidths;
        this.requiredColumns = requiredColumns;
    }

    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }

    public List<StationConfigurationDto> getData() { return data; }
    public void setData(List<StationConfigurationDto> data) { this.data = data; }

    public Map<String, Double> getColumnWidths() { return columnWidths; }
    public void setColumnWidths(Map<String, Double> columnWidths) { this.columnWidths = columnWidths; }

    public List<String> getRequiredColumns() { return requiredColumns; }
    public void setRequiredColumns(List<String> requiredColumns) { this.requiredColumns = requiredColumns; }
}
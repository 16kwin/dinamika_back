package com.example.dinamika_back.dto;

import java.util.List;
import java.util.Map;

public class EnterpriseListResponse {
    private List<String> columns;
    private List<EnterpriseFlatDto> data;
    private Map<String, Double> columnWidths;
    private List<String> requiredColumns;

    public EnterpriseListResponse() {}

    public EnterpriseListResponse(List<String> columns, List<EnterpriseFlatDto> data,
                                  Map<String, Double> columnWidths, List<String> requiredColumns) {
        this.columns = columns;
        this.data = data;
        this.columnWidths = columnWidths;
        this.requiredColumns = requiredColumns;
    }

    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }

    public List<EnterpriseFlatDto> getData() { return data; }
    public void setData(List<EnterpriseFlatDto> data) { this.data = data; }

    public Map<String, Double> getColumnWidths() { return columnWidths; }
    public void setColumnWidths(Map<String, Double> columnWidths) { this.columnWidths = columnWidths; }

    public List<String> getRequiredColumns() { return requiredColumns; }
    public void setRequiredColumns(List<String> requiredColumns) { this.requiredColumns = requiredColumns; }
}
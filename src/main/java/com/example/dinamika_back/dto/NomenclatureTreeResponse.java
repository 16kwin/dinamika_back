// NomenclatureTreeResponse.java
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
public class NomenclatureTreeResponse {
    private List<GroupMaterialTreeDTO> tree;
    private List<String> columns;
    private Map<String, Double> columnWidths;
    private List<String> requiredColumns;
    private String columnsJson;
    private String filtersJson;
    private String sortJson;
    private String currentPathJson;
}
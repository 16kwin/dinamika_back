// SaveBatchCellsRequest.java — ПОЛНЫЙ ФАЙЛ
package com.example.dinamika_back.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class SaveBatchCellsRequest {
    private List<BatchCellItem> cells;

    @Data
    public static class BatchCellItem {
        private Integer numberCell;
        private Integer columnNumber;
        private Integer drumNumber;
        private UUID cellAssignmentUid;
        private UUID materialUid;
        private Integer quantity;
        private Boolean returnToThisCell;
        private Boolean isIndividual;
    }
}
// dto/SaveBatchCellsRequest.java
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
        private UUID materialUid;
        private Integer quantity;
        private UUID typeMainUid;
        private String purposeMaterial;
        private String purposeSgd;
        private Integer maxQuantity;
        private String dimensions;
    }
}
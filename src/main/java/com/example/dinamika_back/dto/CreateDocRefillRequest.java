package com.example.dinamika_back.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDocRefillRequest {

    private UUID uid;
    private LocalDateTime docDatetime;
    private String status;
    private String stationUid;
    private UUID templateUid;
    private Integer createdByUserId;

    private List<RefillCellRequest> cells;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RefillCellRequest {

        private Integer drumNumber;
        private Integer columnNumber;
        private Integer numberCell;

        private UUID cellAssignmentUid;
        private UUID materialUid;

        private Integer templateQuantity;
        private Integer factQuantity;
        private Integer refillQuantity;

        private Integer minStockSnapshot;
        private Integer criticalStockSnapshot;
        private Boolean stockControlExists;

        private Boolean returnToThisCell;
        private Boolean isIndividual;
    }
}
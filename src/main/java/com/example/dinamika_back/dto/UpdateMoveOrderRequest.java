// UpdateMoveOrderRequest.java
package com.example.dinamika_back.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMoveOrderRequest {
    private LocalDateTime docDatetime;
    private String stockToUid;
    private String comment;
    private Boolean useApproval;
    private List<UpdateMoveOrderLineRequest> lines;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateMoveOrderLineRequest {
        private java.util.UUID uid;
        private java.util.UUID materialUid;
        private Integer quantity;
        private String stockFromUid;
        private String priority;
        private LocalDateTime dueDate;
        private String status;
        private Boolean cancelled;
        private String comment;
    }
}
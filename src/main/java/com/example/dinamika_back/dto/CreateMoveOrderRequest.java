// CreateMoveOrderRequest.java
package com.example.dinamika_back.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateMoveOrderRequest {
    private UUID uid;
    private LocalDateTime docDatetime;
    private String stockToUid;
    private String comment;
    private List<MoveOrderLineRequest> lines;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MoveOrderLineRequest {
        private UUID materialUid;
        private Integer quantity;
        private String stockFromUid;
        private String priority;
        private LocalDateTime dueDate;
        private String comment;
    }
}
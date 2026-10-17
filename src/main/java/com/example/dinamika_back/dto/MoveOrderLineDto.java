// MoveOrderLineDto.java
package com.example.dinamika_back.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoveOrderLineDto {
    private UUID uid;
    private UUID docUid;
    private UUID materialUid;
    private String materialName;
    private String materialArticle;
    private Integer materialCode;
    private Integer quantity;
    private String stockFromUid;
    private String stockFromName;
    private Integer available;
    private String priority;
    private LocalDateTime dueDate;
    private String status;
    private LocalDateTime shippingAt;
    private LocalDateTime shippedAt;
    private LocalDateTime loadingAt;
    private LocalDateTime closedAt;
    private Boolean cancelled;
    private UUID transferUid;
    private Integer transferCode;
    private String responsibleName;
    private String comment;
    private Integer acceptedQuantity;
}
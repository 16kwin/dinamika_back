// CreateSupplyRequest.java
package com.example.dinamika_back.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CreateSupplyRequest {
    private UUID supplierUid;
    private LocalDateTime supplyDate;
    private String documentName;
}
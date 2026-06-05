// MaterialSupplyDTO.java
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterialSupplyDTO {
    private UUID uid;
    private UUID materialUid;
    private UUID supplierUid;
    private String supplierName;
    private LocalDateTime supplyDate;
    private String documentName;
    private String filePath;
    private String originalName;
    private String fileUrl;
}
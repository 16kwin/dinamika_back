// WorkshopFlatDto.java — с description и address
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkshopFlatDto {
    private Long id;
    private String name;
    private String description;
    private String address;
    private Long holdingId;
    private String holdingName;
    private Long enterpriseId;
    private String enterpriseName;
    private UUID locationUid;
    private String locationName;
}
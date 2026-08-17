// UpdateWorkshopRequest.java — с description и address
package com.example.dinamika_back.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class UpdateWorkshopRequest {
    private String name;
    private String description;
    private String address;
    private Long enterpriseId;
    private UUID locationUid;
}
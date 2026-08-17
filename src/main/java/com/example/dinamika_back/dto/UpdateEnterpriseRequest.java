package com.example.dinamika_back.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class UpdateEnterpriseRequest {
    private String name;
    private String description;
    private String address;
    private Long holdingId;
    private UUID locationUid;
}
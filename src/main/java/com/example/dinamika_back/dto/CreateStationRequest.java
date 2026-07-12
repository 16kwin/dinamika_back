// CreateStationRequest.java
package com.example.dinamika_back.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CreateStationRequest {
    private String uid;
    private String name;
    private String description;
    private LocalDate productionDate;
    private String serialNumber;
    
    private String modelId;
    private String configurationUid;
    
    private Long holdingId;
    private Long enterpriseId;
    private Long workshopId;
    private Long sectionId;
    
    private String status;
    private String parentUid;
    
    private Boolean isAdditionalModule;
    private Boolean hasAdditionalModule;
    
    private Boolean hasError;
    private Boolean isTmc;
    private Boolean isSgd;
    private Boolean isOk;
    
    private String ipAddress;
    private Integer networkPort;
}
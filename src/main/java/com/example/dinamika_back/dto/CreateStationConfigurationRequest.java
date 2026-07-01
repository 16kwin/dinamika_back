// CreateStationConfigurationRequest.java
package com.example.dinamika_back.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateStationConfigurationRequest {
    private UUID uid;
    private String name;
    private UUID modelId;
    private String cellsStructure;
}
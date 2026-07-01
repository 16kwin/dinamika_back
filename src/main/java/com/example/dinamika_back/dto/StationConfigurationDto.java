// StationConfigurationDto.java
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StationConfigurationDto {
    private UUID uid;
    private String name;
    private UUID modelId;
    private String modelName;
    private String cellsStructure;
}

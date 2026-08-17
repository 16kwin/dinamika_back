// StationConfigurationDto.java — ОБНОВЛЕННЫЙ (добавлен @Builder)
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StationConfigurationDto {
    private UUID uid;
    private String name;
    private UUID modelId;
    private String modelName;
    private String cellsStructure;
}
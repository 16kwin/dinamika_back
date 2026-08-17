// StationModelDto.java — ОБНОВЛЕННЫЙ (добавлен @Builder)
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
public class StationModelDto {
    private UUID uid;
    private Integer code;
    private String name;
    private String article;
    private String revision;
    private UUID typeId;
    private String typeName;
    private UUID manufacturerId;
    private String manufacturerName;
    private String purpose;
    private String cellsStructure;
}
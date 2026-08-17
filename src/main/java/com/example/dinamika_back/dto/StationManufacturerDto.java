// StationManufacturerDto.java — ОБНОВЛЕННЫЙ (добавлена страна)
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
public class StationManufacturerDto {
    private UUID uid;
    private String name;
    private String description;
    private UUID countryUid;
    private String countryName;
}
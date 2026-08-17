// UpdateStationManufacturerRequest.java — ОБНОВЛЕННЫЙ
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
public class UpdateStationManufacturerRequest {
    private String name;
    private String description;
    private UUID countryUid;
}
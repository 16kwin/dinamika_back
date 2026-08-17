// HoldingFlatDto.java — ОБНОВЛЕННЫЙ
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
public class HoldingFlatDto {
    private Long id;
    private String name;
    private String description;
    private UUID locationUid;
    private String locationName;
}
// StationColumnSettingsDto.java
package com.example.dinamika_back.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StationColumnSettingsDto {
    private Integer userId;
    private String columnsJson;
}
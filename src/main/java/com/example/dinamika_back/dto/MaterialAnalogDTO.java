// MaterialAnalogDTO.java
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterialAnalogDTO {
    private UUID uid;
    private UUID materialUid;
    private UUID analogMaterialUid;
    private String analogMaterialName;
    private String analogModelName;
    private Integer compatibilityPercent;
    private LocalDateTime createdAt;
}
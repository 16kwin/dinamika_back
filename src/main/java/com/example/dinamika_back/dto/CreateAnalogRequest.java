// CreateAnalogRequest.java
package com.example.dinamika_back.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class CreateAnalogRequest {
    private UUID analogMaterialUid;
    private Integer compatibilityPercent;
}
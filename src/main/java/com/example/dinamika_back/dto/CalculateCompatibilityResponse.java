// CalculateCompatibilityResponse.java
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalculateCompatibilityResponse {
    private Integer compatibilityPercent;
    private int totalCharacteristics;
    private int matchedCharacteristics;
    private boolean groupsMatch;
}
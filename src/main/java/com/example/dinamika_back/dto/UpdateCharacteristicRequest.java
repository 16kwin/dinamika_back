// ==================== НОВЫЙ ФАЙЛ: UpdateCharacteristicRequest.java ====================
package com.example.dinamika_back.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class UpdateCharacteristicRequest {
    private String value;
    private UUID measureUid;
}
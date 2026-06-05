// ==================== НОВЫЙ ФАЙЛ: CreateCharacteristicRequest.java ====================
package com.example.dinamika_back.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class CreateCharacteristicRequest {
    private UUID attributeTypeUid;  // uid из spr_type_attributes (предопределённая характеристика)
    private String customName;       // если attributeTypeUid == null, то пользовательская характеристика
    private String value;            // значение
    private UUID measureUid;         // единица измерения
}
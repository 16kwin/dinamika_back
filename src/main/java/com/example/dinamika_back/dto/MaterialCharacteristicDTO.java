// ==================== НОВЫЙ ФАЙЛ: MaterialCharacteristicDTO.java ====================
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;

@Data
@AllArgsConstructor
public class MaterialCharacteristicDTO {
    private UUID uid;
    private UUID materialUid;
    private UUID attributeTypeUid;      // uid характеристики из spr_type_attributes (null для пользовательских)
    private String attributeName;        // название характеристики
    private String customName;           // название пользовательской характеристики
    private String value;                // значение
    private UUID measureUid;             // uid единицы измерения
    private String measureName;          // название единицы измерения
    private Boolean isCustom;            // true — пользовательская
}
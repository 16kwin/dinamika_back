package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Вид номенклатуры для выбора в карточках панели.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashTypeDTO {
    private String key;
    private String name;
}

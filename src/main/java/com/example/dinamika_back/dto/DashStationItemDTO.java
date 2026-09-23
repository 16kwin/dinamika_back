package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Строка pop-up станции: номенклатура с критическим или минимальным остатком. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashStationItemDTO {
    private Long id;
    private String name;
    private Integer quantity;
    private Integer minLevel;
    private Integer criticalLevel;
    /** 'critical' — КО, 'minimal' — МО, 'normal' — остаток выше порогов */
    private String status;
}

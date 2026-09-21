package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Столбик графика «Критические и минимальные остатки по станциям».
 * На график выводится номенклатура станции с наименьшим остатком, полный список — в pop-up.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashStationDTO {
    private String key;
    private String name;
    /** Наименьший остаток среди номенклатуры станции */
    private Integer quantity;
    /** Наименование номенклатуры с этим остатком */
    private String nomName;
    private Integer minLevel;
    private Integer criticalLevel;
    /** Статус станции по этой номенклатуре: 'critical' — КО, 'minimal' — МО, 'normal' */
    private String status;
    private List<DashStationItemDTO> items;
}

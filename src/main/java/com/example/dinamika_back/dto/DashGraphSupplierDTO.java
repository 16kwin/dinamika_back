package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Поставщик графа закупок. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashGraphSupplierDTO {
    private String key;
    private String name;
    /** Якорный (основной) поставщик */
    private Boolean anchor;
    private String inn;
    private String city;
}

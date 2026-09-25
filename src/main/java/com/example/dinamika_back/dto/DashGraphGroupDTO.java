package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Группа номенклатуры графа закупок. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashGraphGroupDTO {
    private String key;
    private String name;
}

package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SprTypeProductDTO {
    private UUID uid;
    private String typeName;
    private UUID typePurposeUid;
    /** Название группы номенклатуры */
    private String typePurposeName;
    /** Название группы учета (подтягивается через purpose -> material) */
    private String typeMaterialName;
}
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SprTypePurposeDTO {
    private UUID uid;
    private String typeName;
    private UUID typeMaterialUid;
    /** Название группы учета, чтобы не делать лишний запрос с фронтенда */
    private String typeMaterialName;
}
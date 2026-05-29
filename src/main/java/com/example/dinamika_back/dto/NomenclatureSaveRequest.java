package com.example.dinamika_back.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class NomenclatureSaveRequest {
    private UUID uid;
    private Integer code;
    private String name;
    private String article;
    private String description;
    private UUID groupUid;         // было — группа в дереве каталога
    
    // НОВЫЕ ПОЛЯ:
    private UUID typeMainUid;      // группа учета (ТМЦ, Готовые детали)
    private UUID typePurposeUid;   // группа номенклатуры (Металлообрабатывающий инструмент и т.д.)
    private UUID typeProductUid;   // вид номенклатуры (Сверло, Фреза и т.д.)
}
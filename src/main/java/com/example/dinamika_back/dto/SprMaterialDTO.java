package com.example.dinamika_back.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class SprMaterialDTO {
    private UUID uid;
    private Integer code;
    private String name;
    private String article;
    private String description;
    
    // Каталог (дерево групп)
    private UUID groupUid;
    private String groupName;
    
    // НОВЫЕ ПОЛЯ:
    private UUID typeMainUid;
    private String typeMainName;         // "ТМЦ" или "Готовая деталь"
    
    private UUID typePurposeUid;
    private String typePurposeName;      // "Металлообрабатывающий инструмент" и т.д.
    
    private UUID typeProductUid;
    private String typeProductName;      // "Сверло", "Фреза" и т.д.
}
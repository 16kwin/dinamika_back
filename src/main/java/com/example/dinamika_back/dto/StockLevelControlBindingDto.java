package com.example.dinamika_back.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockLevelControlBindingDto {
    private UUID uid;
    private UUID materialUid;
    private String materialName;
    private String materialArticle;
    private Integer materialCode;
    private LocalDate bindingDate;
    private Integer minStock;
    private Integer criticalStock;
}
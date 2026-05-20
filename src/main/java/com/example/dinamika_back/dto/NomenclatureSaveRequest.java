package com.example.dinamika_back.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
public class NomenclatureSaveRequest {
    private UUID uid;
    private Integer code;
    private String name;
    private String article;
    private String description;
    private UUID groupUid;
}
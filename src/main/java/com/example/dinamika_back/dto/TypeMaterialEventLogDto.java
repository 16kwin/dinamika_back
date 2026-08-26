package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypeMaterialEventLogDto {
    private UUID uid;
    private UUID typeMaterialUid;
    private String eventType;
    private String eventDescription;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String author;
    private String source;
    private LocalDateTime createdAt;
}
// CellAssignmentDto.java — НОВЫЙ ФАЙЛ
package com.example.dinamika_back.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CellAssignmentDto {
    private UUID uid;
    private String name;
    private UUID typeUid;
    private String typeName;
}
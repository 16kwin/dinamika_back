package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManufacturerMediaDTO {
    private UUID uid;
    private UUID manufacturerUid;
    private String filePath;
    private String originalName;
    private String fileUrl;
    private Integer sortOrder;
}
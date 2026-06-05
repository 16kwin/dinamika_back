// MaterialDocumentDTO.java
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterialDocumentDTO {
    private UUID uid;
    private UUID materialUid;
    private String documentName;
    private String filePath;
    private String originalName;
    private String url;
    private LocalDateTime createdAt;
}
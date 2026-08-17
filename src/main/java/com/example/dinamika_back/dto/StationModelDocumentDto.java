// StationModelDocumentDto.java
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
public class StationModelDocumentDto {
    private UUID uid;
    private UUID modelUid;
    private String documentName;
    private String filePath;
    private String originalName;
    private String url;
    private LocalDateTime createdAt;
}
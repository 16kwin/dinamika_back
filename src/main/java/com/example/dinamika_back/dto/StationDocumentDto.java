package com.example.dinamika_back.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StationDocumentDto {
    private UUID uid;
    private String stationUid;
    private String documentName;
    private String filePath;
    private String originalName;
    private String url;
    private LocalDateTime createdAt;
}
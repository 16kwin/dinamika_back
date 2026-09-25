package com.example.dinamika_back.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateEventLogDto {
    private UUID uid;
    private UUID templateUid;
    private UUID cellUid;
    private String eventType;
    private String eventDescription;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String author;
    private String source;
    private LocalDateTime createdAt;
}
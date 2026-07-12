package com.example.dinamika_back.model;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "station_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StationDocument {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @Column(name = "station_uid", nullable = false)
    private String stationUid;

    @Column(name = "document_name")
    private String documentName;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
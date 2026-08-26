package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "type_material_event_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypeMaterialEventLog {

    @Id
    @Column(name = "uid")
    private UUID uid;

    @Column(name = "type_material_uid")
    private UUID typeMaterialUid;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_description", nullable = false)
    private String eventDescription;

    @Column(name = "field_name")
    private String fieldName;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "author")
    private String author;

    @Column(name = "source")
    private String source;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
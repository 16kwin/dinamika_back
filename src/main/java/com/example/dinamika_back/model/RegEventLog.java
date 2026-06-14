package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reg_event_log")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RegEventLog {
    @Id
    private UUID uid;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_uid")
    private SprMaterial material;
    
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;
    
    @Column(name = "event_description", nullable = false, columnDefinition = "text")
    private String eventDescription;
    
    @Column(name = "field_name", length = 255)
    private String fieldName;
    
    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;
    
    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;
    
    @Column(name = "author", length = 255)
    private String author;
    
    @Column(name = "source", length = 100)
    private String source;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
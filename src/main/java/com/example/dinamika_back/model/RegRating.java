// RegRating.java
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reg_rating")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegRating {

    @Id
    private UUID uid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_uid", nullable = false)
    private SprMaterial material;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "comment")
    private String comment;

    @Column(name = "author")
    private String author;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
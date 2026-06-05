// MaterialRatingDTO.java
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterialRatingDTO {
    private UUID uid;
    private UUID materialUid;
    private Integer rating;
    private String comment;
    private String author;
    private LocalDateTime createdAt;
}
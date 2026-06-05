// SprTypeAttributeDTO.java
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SprTypeAttributeDTO {
    private UUID uid;
    private String name;
    private String designation;
}
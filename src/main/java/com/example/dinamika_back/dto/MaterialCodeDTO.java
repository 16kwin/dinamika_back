// MaterialCodeDTO.java
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterialCodeDTO {
    private UUID uid;
    private UUID materialUid;
    private String filePath;
    private String originalName;
    private String codeType;
    private String codeValue;
    private String codeKind;
    private String fileUrl;
    private LocalDateTime createdAt;
}
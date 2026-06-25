// dto/TemplateCopyRequest.java
package com.example.dinamika_back.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class TemplateCopyRequest {
    private UUID sourceTemplateUid;
    private Long targetCategoryId;
}
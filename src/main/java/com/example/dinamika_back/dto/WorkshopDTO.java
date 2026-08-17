// WorkshopDTO.java — ОБНОВЛЕННЫЙ
package com.example.dinamika_back.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class WorkshopDTO {
    private Long id;
    private String name;
    private Long enterpriseId;
    private Long holdingId;
    private String holdingName;
    private UUID locationUid;
    private String locationName;
    private List<SectionDTO> sections = new ArrayList<>();
}
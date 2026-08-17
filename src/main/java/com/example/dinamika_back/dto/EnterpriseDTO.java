// EnterpriseDTO.java — ОБНОВЛЕННЫЙ
package com.example.dinamika_back.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class EnterpriseDTO {
    private Long id;
    private String name;
    private Long holdingId;
    private String holdingName;
    private UUID locationUid;
    private String locationName;
    private List<WorkshopDTO> workshops = new ArrayList<>();
}
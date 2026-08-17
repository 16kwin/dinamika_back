// HoldingDTO.java — ОБНОВЛЕННЫЙ
package com.example.dinamika_back.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class HoldingDTO {
    private Long id;
    private String name;
    private String description;
    private UUID locationUid;
    private String locationName;
    private List<EnterpriseDTO> enterprises = new ArrayList<>();
}
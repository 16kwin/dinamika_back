// WorkshopDTO.java
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkshopDTO {
    private Long id;
    private String name;
    private Long holdingId;
    private String holdingName;
    private Long enterpriseId;
    private List<SectionDTO> sections = new ArrayList<>();
}
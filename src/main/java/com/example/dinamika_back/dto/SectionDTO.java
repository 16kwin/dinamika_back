// SectionDTO.java
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SectionDTO {
    private Long id;
    private String name;
    private Long holdingId;
    private String holdingName;
    private Long enterpriseId;
    private Long workshopId;
}
// SectionFlatDto.java
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SectionFlatDto {
    private Long id;
    private String name;
    private Long workshopId;
    private String workshopName;
    private Long enterpriseId;
    private String enterpriseName;
}
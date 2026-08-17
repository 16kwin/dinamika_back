// SectionFlatDto.java — ОБНОВЛЕННЫЙ (добавлен @Builder)
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectionFlatDto {
    private Long id;
    private String name;
    private Long holdingId;
    private String holdingName;
    private Long enterpriseId;
    private String enterpriseName;
    private Long workshopId;
    private String workshopName;
}
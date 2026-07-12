// StationStaticDto.java — ПОЛНЫЙ ФАЙЛ
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StationStaticDto {
    private String uid;
    private String name;
    private Long holdingId;
    private String holdingName;
    private Long enterpriseId;
    private String enterprise;
    private Long workshopId;
    private String workshop;
    private Long sectionId;
    private String section;
    private String status;
    private String stationType;
    private String stationTypeUid;
    private String modelId;
    private String modelName;
    private String configurationUid;
    private String parentUid;
    private Boolean hasError;
    private Boolean isTmc;
    private Boolean isSgd;
    private Boolean isOk;
    private String activeTemplateUid;
    private String activeTemplateName;
}
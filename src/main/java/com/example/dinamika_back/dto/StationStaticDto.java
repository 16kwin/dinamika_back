// StationStaticDto.java
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
    private String workshop;
    private String section;
    private Long enterpriseId;
    private Long workshopId;
    private Long sectionId;
    private String status;
    private String stationType;
    private String parentUid;
    private Boolean hasError;
    private Boolean isTmc;
    private Boolean isSgd;
    private Boolean isOk;
}
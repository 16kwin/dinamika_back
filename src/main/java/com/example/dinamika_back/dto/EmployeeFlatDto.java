// EmployeeFlatDto.java
package com.example.dinamika_back.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeFlatDto {
    private UUID uid;
    private String firstName;
    private String lastName;
    private String middleName;
    private String profession;
    private Long enterpriseId;
    private String enterpriseName;
    private Long workshopId;
    private String workshopName;
    private Long sectionId;
    private String sectionName;
}
// UpdateEmployeeRequest.java
package com.example.dinamika_back.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEmployeeRequest {
    private String firstName;
    private String lastName;
    private String middleName;
    private String profession;
    private Long enterpriseId;
    private Long workshopId;
    private Long sectionId;
}
// CreateSectionRequest.java
package com.example.dinamika_back.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSectionRequest {
    private String name;
    private Long workshopId;
}
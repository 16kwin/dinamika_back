// StationModelImageDto.java
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StationModelImageDto {
    private UUID uid;
    private UUID modelUid;
    private String filePath;
    private String originalName;
    private String url;
    private Integer sortOrder;
}
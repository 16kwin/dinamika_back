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
public class MaterialMediaDTO {
    private UUID uid;
    private UUID materialUid;
    private String filePath;
    private String originalName;
    private String url;
    private Integer sortOrder;
}
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
public class SprModelOfBrandDTO {
    private UUID uid;
    private String name;
    private String description;
    private UUID brandUid;
    private String brandName;
    private String manufacturerName;
}
package com.example.dinamika_back.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CreateMeasureRequest {
    private String name;
    private String description;
    private UUID groupUid;
}
package com.example.dinamika_back.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class UpdateTypeAttributeRequest {
    private String name;
    private String designation;
    private UUID groupUid;
}
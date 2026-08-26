package com.example.dinamika_back.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class UpdateModelRequest {
    private String name;
    private String description;
    private UUID brandUid;
    private UUID manufacturerUid;
}
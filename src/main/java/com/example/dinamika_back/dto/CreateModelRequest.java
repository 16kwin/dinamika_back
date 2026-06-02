package com.example.dinamika_back.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Getter
@Setter
public class CreateModelRequest {
    private String name;
    private String description;
    private UUID brandUid;
}
package com.example.dinamika_back.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
public class CreateGroupRequest {
    private String name;
    private UUID parentUid;
}
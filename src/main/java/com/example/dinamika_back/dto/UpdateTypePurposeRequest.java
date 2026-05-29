package com.example.dinamika_back.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class UpdateTypePurposeRequest {
    private String name;
    private UUID typeMaterialUid;
}
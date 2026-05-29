package com.example.dinamika_back.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class UpdateTypeProductRequest {
    private String name;
    private UUID typePurposeUid;
}
package com.example.dinamika_back.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateManufacturerRequest {
    private String name;
    private String description;
}
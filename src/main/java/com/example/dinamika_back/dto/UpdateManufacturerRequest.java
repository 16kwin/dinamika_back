package com.example.dinamika_back.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class UpdateManufacturerRequest {
    private String name;
    private String description;
    private UUID countryUid;
    private UUID directionUid;
    private String address;
    private String email;
    private String website;
    private String phone;
}
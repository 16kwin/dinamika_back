package com.example.dinamika_back.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class CreateManufacturerRequest {
    private UUID uid;
    private String name;
    private String description;
    private String address;
    private String email;
    private String website;
    private String phone;
    private UUID countryUid;
    private UUID directionUid;
}
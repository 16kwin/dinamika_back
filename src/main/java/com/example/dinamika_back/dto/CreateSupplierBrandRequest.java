package com.example.dinamika_back.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CreateSupplierBrandRequest {
    private String name;
    private UUID supplierUid;
}
// CreateSupplierIntegrationRequest.java
package com.example.dinamika_back.dto;

import lombok.Data;

@Data
public class CreateSupplierIntegrationRequest {
    private String exchangeType;
    private String direction;
    private String protocol;
    private String targetSystem;
}
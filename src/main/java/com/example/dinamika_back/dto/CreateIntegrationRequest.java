// CreateIntegrationRequest.java
package com.example.dinamika_back.dto;

import lombok.Data;

@Data
public class CreateIntegrationRequest {
    private String exchangeType;
    private String direction;
    private String protocol;
    private String targetSystem;
}
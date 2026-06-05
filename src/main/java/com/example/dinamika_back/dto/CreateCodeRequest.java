// CreateCodeRequest.java
package com.example.dinamika_back.dto;

import lombok.Data;

@Data
public class CreateCodeRequest {
    private String codeType;
    private String codeValue;
    private String codeKind;
}
// SupplierCreateResponse.java
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;

@Data
@AllArgsConstructor
public class SupplierCreateResponse {
    private UUID uid;
    private Integer code;
}
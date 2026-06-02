// AddPriceRequest.java
package com.example.dinamika_back.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class AddPriceRequest {
    private Double price;
    private LocalDateTime priceDate;
    private UUID supplierUid;
}
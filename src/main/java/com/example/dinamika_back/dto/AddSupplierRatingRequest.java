// AddSupplierRatingRequest.java
package com.example.dinamika_back.dto;

import lombok.Data;

@Data
public class AddSupplierRatingRequest {
    private Integer rating;
    private String comment;
    private String author;
}
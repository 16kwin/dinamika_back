// AddRatingRequest.java
package com.example.dinamika_back.dto;

import lombok.Data;

@Data
public class AddRatingRequest {
    private Integer rating;
    private String comment;
    private String author;
}
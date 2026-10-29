// AddRatingRequest.java
package com.example.dinamika_back.dto;

import lombok.Data;

@Data
public class AddRatingRequest {
    private Integer rating;
    private String comment;
    /** Не используется: подпись отзыва берётся из входа (старые клиенты ещё присылают) */
    private String author;
}
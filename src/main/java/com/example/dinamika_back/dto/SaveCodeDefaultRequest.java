package com.example.dinamika_back.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveCodeDefaultRequest {
    private Integer userId;
    private String codeKind;
    private String codeType;
}
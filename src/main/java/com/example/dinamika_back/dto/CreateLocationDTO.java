package com.example.dinamika_back.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateLocationDTO {
    private String name;
    private Integer level; // 1, 2 или 3
    private Integer parentId; // null для завода, ID родителя для цеха/участка
}
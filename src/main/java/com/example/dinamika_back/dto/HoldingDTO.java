// HoldingDTO.java
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HoldingDTO {
    private Long id;
    private String name;
    private String description;
    private List<EnterpriseDTO> enterprises = new ArrayList<>();
}
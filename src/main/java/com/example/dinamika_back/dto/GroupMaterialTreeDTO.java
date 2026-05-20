// dto/GroupMaterialTreeDTO.java
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupMaterialTreeDTO {
    private UUID uid;
    private String name;
    private List<GroupMaterialTreeDTO> children = new ArrayList<>();
    private List<MaterialItemDTO> materials = new ArrayList<>();
}
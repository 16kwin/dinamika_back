// dto/GroupMaterialTreeDTO.java (обновлённый)
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
    private Integer code;
    private List<GroupMaterialTreeDTO> children = new ArrayList<>();
    private List<MaterialItemDTO> materials = new ArrayList<>();
}
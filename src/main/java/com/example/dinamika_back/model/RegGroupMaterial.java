package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "reg_group_material")
@Getter
@Setter
@NoArgsConstructor
public class RegGroupMaterial {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @Column(name = "group_name", nullable = false)
    private String groupName;

    @Column(name = "parent_group")
    private UUID parentGroup;

    @Column(name = "group_code")
    private Integer groupCode;
}
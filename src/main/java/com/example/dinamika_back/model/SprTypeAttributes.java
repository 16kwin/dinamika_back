package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "spr_type_attributes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SprTypeAttributes {

    @Id
    private UUID uid;

    @Column(name = "name")
    private String name;

    @Column(name = "designation")
    private String designation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_uid")
    private SprAttributeGroup group;
}
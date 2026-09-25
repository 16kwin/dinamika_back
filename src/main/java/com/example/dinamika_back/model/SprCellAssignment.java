// SprCellAssignment.java — НОВЫЙ ФАЙЛ
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "spr_cell_assignment")
@Getter
@Setter
@NoArgsConstructor
public class SprCellAssignment {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_uid")
    private SprTypeMaterial type;
}
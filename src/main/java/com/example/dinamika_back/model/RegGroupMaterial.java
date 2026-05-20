package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Регистр "Группы материалов".
 * Иерархическая структура групп для классификации номенклатуры.
 */
@Entity
@Table(name = "reg_group_material")
@Getter
@Setter
@NoArgsConstructor
public class RegGroupMaterial {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    /** Наименование группы */
    @Column(name = "group_name", nullable = false)
    private String groupName;

    /** Идентификатор родительской группы (UUID) */
    @Column(name = "parent_group")
    private UUID parentGroup;
}
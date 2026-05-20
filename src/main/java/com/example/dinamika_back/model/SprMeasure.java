package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Справочник "Единицы измерения".
 * Содержит перечень единиц измерения (шт, кг, м и т.д.).
 */
@Entity
@Table(name = "spr_measure")
@Getter
@Setter
@NoArgsConstructor
public class SprMeasure {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    /** Наименование единицы измерения */
    @Column(name = "name", nullable = false)
    private String name;
}
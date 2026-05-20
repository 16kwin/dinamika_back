package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Справочник "Виды атрибутов".
 * Определяет возможные характеристики (атрибуты) для номенклатуры.
 * Каждый вид атрибута имеет свой тип данных (текст, число, справочник).
 */
@Entity
@Table(name = "spr_type_attributes")
@Getter
@Setter
@NoArgsConstructor
public class SprTypeAttributes {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    /** Наименование вида атрибута */
    @Column(name = "name", nullable = false)
    private String name;

    /** Тип данных значения атрибута */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_type")
    private DataType dataType;
}
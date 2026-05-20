package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Справочник "Виды товара".
 * Классификация товаров по видам.
 */
@Entity
@Table(name = "spr_type_product")
@Getter
@Setter
@NoArgsConstructor
public class SprTypeProduct {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    /** Наименование вида товара */
    @Column(name = "type_name", nullable = false)
    private String typeName;
}
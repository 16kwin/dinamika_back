package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Справочник "Модели брендов".
 * Содержит конкретные модели продукции в рамках определённого бренда.
 */
@Entity
@Table(name = "spr_model_of_brand")
@Getter
@Setter
@NoArgsConstructor
public class SprModelOfBrand {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    /** Наименование модели */
    @Column(name = "name", nullable = false)
    private String name;

    /** Бренд, к которому относится модель */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand")
    private SprBrand brand;
}
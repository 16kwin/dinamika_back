package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Справочник "Бренды".
 * Содержит перечень брендов (торговых марок) продукции.
 */
@Entity
@Table(name = "spr_brand")
@Getter
@Setter
@NoArgsConstructor
public class SprBrand {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    /** Наименование бренда */
    @Column(name = "name", nullable = false)
    private String name;
}
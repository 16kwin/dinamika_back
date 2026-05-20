package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Справочник "Производители".
 * Содержит перечень производителей продукции.
 */
@Entity
@Table(name = "spr_manufacturer")
@Getter
@Setter
@NoArgsConstructor
public class SprManufacturer {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    /** Наименование производителя */
    @Column(name = "name", nullable = false)
    private String name;
}
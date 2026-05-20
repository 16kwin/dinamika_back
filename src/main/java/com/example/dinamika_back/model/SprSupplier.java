package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Справочник "Поставщики".
 * Содержит перечень поставщиков материалов.
 */
@Entity
@Table(name = "spr_suppliers")
@Getter
@Setter
@NoArgsConstructor
public class SprSupplier {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    /** Наименование поставщика */
    @Column(name = "name", nullable = false)
    private String name;
}
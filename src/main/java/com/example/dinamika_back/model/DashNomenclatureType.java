package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Вид номенклатуры для панели «Экономический блок» (dash_nomenclature_type).
 */
@Entity
@Table(name = "dash_nomenclature_type")
@Getter
@Setter
@NoArgsConstructor
public class DashNomenclatureType {

    /** Ключ вида — используется в настройках пользователя и в API */
    @Id
    @Column(name = "type_key", nullable = false, length = 64)
    private String typeKey;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /** Порядок на макете */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}

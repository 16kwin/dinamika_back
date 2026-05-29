package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Справочник "Типы назначения материалов".
 * Классификация по назначению: Металлообрабатывающий инструмент,
 * Механический инструмент, Оснастка, СИЗ, Расходные материалы и др.
 * Теперь привязан к группе учета (typeMaterial).
 */
@Entity
@Table(name = "spr_type_purpose")
@Getter
@Setter
@NoArgsConstructor
public class SprTypePurpose {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    /** Наименование типа назначения */
    @Column(name = "type_name", nullable = false)
    private String typeName;

    /** 
     * Группа учета, к которой относится этот тип назначения.
     * Например: "Металлообрабатывающий инструмент" относится к группе учета "ТМЦ".
     * Добавлено для каскадной фильтрации: выбрал ТМЦ -> видишь только инструменты.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_material_uid")
    private SprTypeMaterial typeMaterial;
}
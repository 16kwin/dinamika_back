package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Справочник "Виды товара".
 * Классификация товаров по видам.
 * Теперь привязан к группе номенклатуры (typePurpose).
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

    /**
     * Группа номенклатуры, к которой относится этот вид.
     * Например: "Сверло" относится к "Металлообрабатывающий инструмент".
     * Добавлено для каскадной фильтрации: выбрал группу -> видишь только её виды.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_purpose_uid")
    private SprTypePurpose typePurpose;
}
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Регистр "Привязка ячеек к шаблону пополнения станции".
 * Определяет, какой материал и в каком количестве должен находиться
 * в конкретной ячейке согласно шаблону пополнения.
 */
@Entity
@Table(name = "reg_cells")
@Getter
@Setter
@NoArgsConstructor
public class RegCells {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    /** Шаблон пополнения, к которому относится ячейка */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doc_pattern_uid")
    private DocPattern docPattern;

    /** Номер ячейки */
    @Column(name = "number_cell")
    private Integer numberCell;

    /** Материал, закреплённый за ячейкой */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "name_material")
    private SprMaterial material;

    /** Количество материала в ячейке */
    @Column(name = "quantity")
    private Integer quantity;

    /** Тип материала в ячейке (ТМЦ, готовая деталь, и т.д.) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_main")
    private SprTypeMaterial typeMain;
}
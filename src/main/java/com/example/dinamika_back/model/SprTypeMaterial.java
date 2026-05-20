package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Справочник "Основные виды хранимых материалов".
 * Категории: ТМЦ, Готовая деталь, Инструмент на переточку, Брак готовой детали, Лом.
 */
@Entity
@Table(name = "spr_type_material")
@Getter
@Setter
@NoArgsConstructor
public class SprTypeMaterial {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    /** Наименование вида материала */
    @Column(name = "type_name", nullable = false)
    private String typeName;
}
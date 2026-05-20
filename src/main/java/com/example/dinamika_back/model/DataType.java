package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Типы данных для атрибутов номенклатуры.
 * Определяет, какого типа значение может хранить атрибут (текст, число, ссылка на справочник).
 */
@Entity
@Table(name = "data_type")
@Getter
@Setter
@NoArgsConstructor
public class DataType {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    /** Текстовое значение атрибута */
    @Column(name = "type_text")
    private String typeText;

    /** Числовое значение атрибута */
    @Column(name = "type_number")
    private Double typeNumber;

    /** Ссылка на значение из справочника */
    @Column(name = "type_spr")
    private UUID typeSpr;
}
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Регистр "Атрибуты".
 * Хранит значения атрибутов, назначенных номенклатуре.
 * Каждый атрибут имеет тип (из справочника SprTypeAttributes) и значение.
 */
@Entity
@Table(name = "reg_attributes")
@Getter
@Setter
@NoArgsConstructor
public class RegAttributes {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    /** Вид атрибута (ссылка на справочник типов атрибутов) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "name")
    private SprTypeAttributes attributeType;

    /** Значение атрибута */
    @Column(name = "meaning")
    private String meaning;
}
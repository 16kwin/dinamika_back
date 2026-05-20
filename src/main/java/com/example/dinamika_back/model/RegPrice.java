package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Регистр "Цены".
 * Хранит историю цен на материалы с привязкой к документу поступления.
 */
@Entity
@Table(name = "reg_price")
@Getter
@Setter
@NoArgsConstructor
public class RegPrice {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    /** Значение цены */
    @Column(name = "price", nullable = false)
    private Double price;

    /** Связь с материалом */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "link")
    private SprMaterial material;

    /** Документ поступления, на основании которого установлена цена */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doc_entrance_uid")
    private DocEntrance docEntrance;
}
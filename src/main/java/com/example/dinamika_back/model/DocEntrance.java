package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Документ "Поступление материалов" (приходная накладная, УПД).
 * Фиксирует факт поступления ТМЦ от поставщика с указанием цены.
 */
@Entity
@Table(name = "doc_entrance")
@Getter
@Setter
@NoArgsConstructor
public class DocEntrance {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    /** Цена поступления */
    @Column(name = "price", nullable = false)
    private Double price;

    /** Поставщик, от которого поступил материал */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier")
    private SprSupplier supplier;
}
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Регистр "Поставщики с привязкой к номенклатуре".
 * Устанавливает связь между материалом и его поставщиком.
 */
@Entity
@Table(name = "reg_suppliers")
@Getter
@Setter
@NoArgsConstructor
public class RegSuppliers {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    /** Материал */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_uid")
    private SprMaterial material;

    /** Поставщик данного материала */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_uid")
    private SprSupplier supplier;
}
// ==================== ИСПРАВЛЕННЫЙ ФАЙЛ: RegAttributes.java ====================
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "reg_attributes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegAttributes {

    @Id
    private UUID uid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "name")
    private SprTypeAttributes attributeType;  // Тип атрибута (характеристика)

    @Column(name = "meaning")
    private String meaning;  // Значение

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measure_uid")
    private SprMeasure measure;  // Единица измерения

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_uid")
    private SprMaterial material;  // Привязка к материалу
}
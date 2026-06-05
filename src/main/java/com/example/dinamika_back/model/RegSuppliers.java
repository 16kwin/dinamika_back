// RegSuppliers.java — ОБНОВЛЁННАЯ модель
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reg_suppliers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegSuppliers {

    @Id
    private UUID uid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_uid")
    private SprMaterial material;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_uid")
    private SprSupplier supplier;

    @Column(name = "supply_date")
    private LocalDateTime supplyDate;

    @Column(name = "document_name")
    private String documentName;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "original_name")
    private String originalName;
}
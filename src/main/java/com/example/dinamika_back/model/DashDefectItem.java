package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Элемент раскрытия субъекта (строка pop-up): подразделение для детали, номенклатура для подразделения.
 */
@Entity
@Table(name = "dash_defect_item")
@IdClass(DashDefectItemId.class)
@Getter
@Setter
@NoArgsConstructor
public class DashDefectItem {

    @Id
    @Column(name = "view_kind", nullable = false, length = 16)
    private String viewKind;

    @Id
    @Column(name = "item_key", nullable = false, length = 64)
    private String itemKey;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}

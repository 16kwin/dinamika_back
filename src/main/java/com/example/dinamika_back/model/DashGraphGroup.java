package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Группа номенклатуры графа закупок (dash_graph_group); sort_order — порядок в легенде и фильтрах.
 */
@Entity
@Table(name = "dash_graph_group")
@Getter
@Setter
@NoArgsConstructor
public class DashGraphGroup {

    @Id
    @Column(name = "group_key", nullable = false, length = 64)
    private String groupKey;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}

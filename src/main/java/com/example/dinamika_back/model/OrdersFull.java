// AWMS — model/OrdersFull.java — ИСПРАВЛЕННЫЙ
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "awms_orders_full")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrdersFull {

    @Id
    @Column(name = "order_uid")
    private String orderUid;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "order_json", columnDefinition = "jsonb", nullable = false)
    private String orderJson;
}
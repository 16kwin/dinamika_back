// AWMS — model/OrdersList.java
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;

@Entity
@Table(name = "awms_orders_list")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrdersList {

    @Id
    @Column(name = "order_uid")
    private String orderUid;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "order_number")
    private String orderNumber;

    @Column(name = "order_datetime")
    private ZonedDateTime orderDatetime;

    @Column(name = "status")
    private String status;

    @Column(name = "statusreason")
    private String statusreason;

    @Column(name = "synced_at")
    private ZonedDateTime syncedAt;
}
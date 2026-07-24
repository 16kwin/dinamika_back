// AWMS — model/TkpList.java
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(name = "awms_tkp_list")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkpList {

    @Id
    @Column(name = "tkp_uid")
    private String tkpUid;

    @Column(name = "order_uid", nullable = false)
    private String orderUid;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "order_number")
    private String orderNumber;

    @Column(name = "order_datetime")
    private ZonedDateTime orderDatetime;

    @Column(name = "total_cost")
    private BigDecimal totalCost;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    @Column(name = "status")
    private String status;

    @Column(name = "statusinvoice")
    private String statusinvoice;

    @Column(name = "synced_at")
    private ZonedDateTime syncedAt;
}
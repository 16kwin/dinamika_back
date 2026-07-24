// AWMS — model/OrderTracking.java
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;

@Entity
@Table(name = "awms_order_tracking")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "datetime", nullable = false)
    private ZonedDateTime datetime;

    @Column(name = "order_uid", nullable = false)
    private String orderUid;

    @Column(name = "tracking_status")
    private String trackingStatus;

    @Column(name = "tracking_sub_status")
    private String trackingSubStatus;
}
// AWMS — model/TkpStatus.java
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;

@Entity
@Table(name = "awms_tkp_statuses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkpStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "datetime", nullable = false)
    private ZonedDateTime datetime;

    @Column(name = "tkp_uid", nullable = false)
    private String tkpUid;

    @Column(name = "order_uid")
    private String orderUid;

    @Column(name = "status")
    private String status;

    @Column(name = "sub_status")
    private String subStatus;
}
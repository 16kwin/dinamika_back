// AWMS — model/TkpFull.java — ИСПРАВЛЕННЫЙ
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "awms_tkp_full")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkpFull {

    @Id
    @Column(name = "tkp_uid")
    private String tkpUid;

    @Column(name = "order_uid", nullable = false)
    private String orderUid;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tkp_json", columnDefinition = "jsonb", nullable = false)
    private String tkpJson;
}
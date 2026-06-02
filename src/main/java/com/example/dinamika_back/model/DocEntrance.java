package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "doc_entrance")
@Getter
@Setter
@NoArgsConstructor
public class DocEntrance {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @Column(name = "price", nullable = false)
    private Double price;

    @Column(name = "entrance_date")
    private LocalDateTime entranceDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier")
    private SprSupplier supplier;
}
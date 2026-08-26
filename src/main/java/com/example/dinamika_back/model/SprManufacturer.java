package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "spr_manufacturer")
@Getter
@Setter
@NoArgsConstructor
public class SprManufacturer {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "code")
    private Integer code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_uid")
    private SprCountry country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "direction_uid")
    private SprProductionDirection direction;

    @Column(name = "address")
    private String address;

    @Column(name = "email")
    private String email;

    @Column(name = "website")
    private String website;

    @Column(name = "phone")
    private String phone;
}
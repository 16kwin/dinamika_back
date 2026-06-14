package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "spr_suppliers")
@Getter
@Setter
@NoArgsConstructor
public class SprSupplier {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @Column(name = "code")
    private Integer code;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne
    @JoinColumn(name = "country_uid")
    private SprCountry country;

    @Column(name = "address")
    private String address;

    @ManyToOne
    @JoinColumn(name = "short_description_uid")
    private SprSupplierDescriptionType shortDescription;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "email")
    private String email;

    @Column(name = "website")
    private String website;

    @Column(name = "phone")
    private String phone;

    @ManyToOne
    @JoinColumn(name = "brand_uid")
    private SprBrand brand;

    // Реквизиты
    @Column(name = "inn")
    private String inn;

    @Column(name = "ogrn")
    private String ogrn;

    @Column(name = "kpp")
    private String kpp;

    @Column(name = "contact_person")
    private String contactPerson;

    @Column(name = "contact_position")
    private String contactPosition;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "director")
    private String director;

    @Column(name = "director_position")
    private String directorPosition;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bik")
    private String bik;

    @Column(name = "correspondent_account")
    private String correspondentAccount;

    @Column(name = "settlement_account")
    private String settlementAccount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
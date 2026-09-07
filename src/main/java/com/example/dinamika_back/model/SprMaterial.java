package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "spr_material")
@Getter
@Setter
@NoArgsConstructor
public class SprMaterial {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @Column(name = "guid_1c", columnDefinition = "bit varying(128)[]")
    private byte[][] guid1C;

    @Column(name = "uid_other_sys", columnDefinition = "bit varying(128)[]")
    private byte[][] uidOtherSys;

    @Column(name = "uid_store", columnDefinition = "bit varying(128)[]")
    private byte[][] uidStore;

    @Column(name = "url_image")
    private UUID urlImage;

    @Column(name = "code_material", nullable = false)
    private Integer codeMaterial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_material")
    private RegGroupMaterial groupMaterial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_main")
    private SprTypeMaterial typeMain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_purpose")
    private SprTypePurpose typePurpose;

    @Column(name = "resharpen")
    private Boolean resharpen;

    @Column(name = "name_material")
    private String nameMaterial;

    @Column(name = "article")
    private String article;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_product")
    private SprTypeProduct typeProduct;

    /** Вид выпуска (полуфабрикат, деталь, продукция) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "release_uid")
    private SprRelease release;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manufacturer")
    private SprManufacturer manufacturer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country")
    private SprCountry country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand")
    private SprBrand brand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_of_brand")
    private SprModelOfBrand modelOfBrand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measure")
    private SprMeasure measure;

    @Column(name = "usage")
    private Boolean usage;

    @Column(name = "waste_material")
    private Boolean wasteMaterial;

    @Column(name = "recycle_material")
    private Boolean recycleMaterial;

    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attached")
    private RegAttached attached;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suppliers")
    private RegSuppliers suppliers;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attributes")
    private RegAttributes attributes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price")
    private RegPrice price;

    @Column(name = "syncronized_mother_system")
    private Boolean syncronizedMotherSystem;

    @Column(name = "syncronized_supplier")
    private Boolean syncronizedSupplier;

    @Column(name = "create_date")
    private LocalTime createDate;
}
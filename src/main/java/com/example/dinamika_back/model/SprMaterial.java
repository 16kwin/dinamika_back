package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Справочник "Номенклатура".
 * Основной объект системы, описывающий товарно-материальные ценности (ТМЦ).
 * Содержит все характеристики материала: артикул, производителя, бренд,
 * единицы измерения, признак переточки, связь с поставщиками, аналогами,
 * атрибутами и ценами.
 */
@Entity
@Table(name = "spr_material")
@Getter
@Setter
@NoArgsConstructor
public class SprMaterial {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    /** GUID материала в системе 1С */
    @Column(name = "guid_1c", columnDefinition = "bit varying(128)[]")
    private byte[][] guid1C;

    /** UID материала во внешней материнской системе */
    @Column(name = "uid_other_sys", columnDefinition = "bit varying(128)[]")
    private byte[][] uidOtherSys;

    /** UID связанного магазина */
    @Column(name = "uid_store", columnDefinition = "bit varying(128)[]")
    private byte[][] uidStore;

    /** Ссылка на изображение материала */
    @Column(name = "url_image")
    private UUID urlImage;

    /** Код номенклатуры (автоинкремент) */
    @Column(name = "code_material", nullable = false)
    private Integer codeMaterial;

    /** Группа материалов */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_material")
    private RegGroupMaterial groupMaterial;

    /** Основной тип хранимого материала (ТМЦ, готовая деталь и т.д.) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_main")
    private SprTypeMaterial typeMain;

    /** Тип назначения (металлообрабатывающий инструмент, оснастка и т.д.) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_purpose")
    private SprTypePurpose typePurpose;

    /** Признак переточенного материала */
    @Column(name = "resharpen")
    private Boolean resharpen;

    /** Наименование ТМЦ */
    @Column(name = "name_material")
    private String nameMaterial;

    /** Артикул */
    @Column(name = "article")
    private String article;

    /** Вид товара */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_product")
    private SprTypeProduct typeProduct;

    /** Производитель */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manufacturer")
    private SprManufacturer manufacturer;

    /** Страна происхождения */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country")
    private SprCountry country;

    /** Бренд товара */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand")
    private SprBrand brand;

    /** Конкретная модель бренда */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_of_brand")
    private SprModelOfBrand modelOfBrand;

    /** Единица измерения */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measure")
    private SprMeasure measure;

    /** Признак одноразового/многоразового использования */
    @Column(name = "usage")
    private Boolean usage;

    /** Признак возврата в лом */
    @Column(name = "waste_material")
    private Boolean wasteMaterial;

    /** Признак возврата на переточку */
    @Column(name = "recycle_material")
    private Boolean recycleMaterial;

    /** Описание */
    @Column(name = "description")
    private String description;

    /** Список приложенных файлов */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attached")
    private RegAttached attached;

    /** Список поставщиков */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suppliers")
    private RegSuppliers suppliers;

    /** Список атрибутов и характеристик */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attributes")
    private RegAttributes attributes;

    /** Ссылка на регистр цен */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price")
    private RegPrice price;

    /** Признак синхронизации с материнской системой */
    @Column(name = "syncronized_mother_system")
    private Boolean syncronizedMotherSystem;

    /** Признак синхронизации с системой поставщика */
    @Column(name = "syncronized_supplier")
    private Boolean syncronizedSupplier;

    /** Дата создания записи */
    @Column(name = "create_date")
    private LocalTime createDate;
}
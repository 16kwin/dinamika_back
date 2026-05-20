package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Справочник "Страны".
 * Содержит перечень стран происхождения товаров.
 */
@Entity
@Table(name = "spr_country")
@Getter
@Setter
@NoArgsConstructor
public class SprCountry {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    /** Наименование страны */
    @Column(name = "name", nullable = false)
    private String name;
}
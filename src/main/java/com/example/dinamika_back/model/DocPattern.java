package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Документ "Шаблон пополнения станции".
 * Определяет номенклатуру и количество материалов, которые должны находиться в ячейках станции.
 */
@Entity
@Table(name = "doc_pattern")
@Getter
@Setter
@NoArgsConstructor
public class DocPattern {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    /** Наименование шаблона */
    @Column(name = "name_pattern", nullable = false)
    private String namePattern;

    /** Статус документа (активен/неактивен) */
    @Column(name = "status_doc", nullable = false)
    private Boolean statusDoc;

    /** Станция, к которой привязан шаблон */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "name_station", referencedColumnName = "uid")
    private Station station;
}
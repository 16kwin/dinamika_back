package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Регистр "Аналоги".
 * Хранит связи между основным материалом и его аналогами.
 */
@Entity
@Table(name = "reg_analog")
@Getter
@Setter
@NoArgsConstructor
public class RegAnalog {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    /** Ссылка на материал-аналог */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "name")
    private SprMaterial material;
}
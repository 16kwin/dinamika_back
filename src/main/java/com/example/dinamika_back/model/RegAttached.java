package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Регистр "Файлы".
 * Хранит информацию о прикреплённых файлах, связанных с номенклатурой.
 */
@Entity
@Table(name = "reg_attached")
@Getter
@Setter
@NoArgsConstructor
public class RegAttached {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    /** Наименование файла */
    @Column(name = "name_file", nullable = false)
    private String nameFile;

    /** Ссылка на файл */
    @Column(name = "url_file", nullable = false)
    private UUID urlFile;

    /** Связь с материалом, к которому прикреплён файл */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "link")
    private SprMaterial material;
}
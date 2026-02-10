package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "location")
@Getter
@Setter
@NoArgsConstructor
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "location_name", nullable = false)
    private String locationName;

    @Column(name = "level", nullable = false)
    private Integer level; // 1, 2 или 3

    // Новые поля для фото
    @Column(name = "photo_file_name")
    private String photoFileName;

    @Column(name = "photo_file_path")
    private String photoFilePath;

    @Column(name = "photo_file_extension")
    private String photoFileExtension;

    public Location(String locationName, Integer level) {
        this.locationName = locationName;
        this.level = level;
    }
}
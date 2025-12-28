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

    public Location(String locationName, Integer level) {
        this.locationName = locationName;
        this.level = level;
    }
}
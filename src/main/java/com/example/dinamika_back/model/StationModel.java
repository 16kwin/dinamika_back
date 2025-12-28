package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "station_model")
@Getter
@Setter
@NoArgsConstructor
public class StationModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "model_number", unique = true, nullable = false)
    private Integer modelNumber;

    public StationModel(Integer modelNumber) {
        this.modelNumber = modelNumber;
    }
}
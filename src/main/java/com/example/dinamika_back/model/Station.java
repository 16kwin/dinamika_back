package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "station")
@Getter
@Setter
@NoArgsConstructor
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "uid")
    private Integer uid;

    @Column(name = "station_name", nullable = false)
    private String stationName;

    @ManyToOne
    @JoinColumn(name = "station_model_id", nullable = false)
    private StationModel stationModel;

    @Column(name = "serial_number")
    private Integer serialNumberOfTheStation;

    @Column(name = "current_capacity")
    private Integer currentCapacityOfTheStation;

    @Column(name = "ip_address")
    private String ipAddress;

    @ManyToOne
    @JoinColumn(name = "level_1_factory_id")
    private Location level1Factory;

    @ManyToOne
    @JoinColumn(name = "level_2_object_id")
    private Location level2Object;

    @ManyToOne
    @JoinColumn(name = "level_3_zone_id")
    private Location level3Zone;

    public Station(String stationName, StationModel stationModel, Integer serialNumberOfTheStation, 
                   Integer currentCapacityOfTheStation, String ipAddress, Location level1Factory, 
                   Location level2Object, Location level3Zone) {
        this.stationName = stationName;
        this.stationModel = stationModel;
        this.serialNumberOfTheStation = serialNumberOfTheStation;
        this.currentCapacityOfTheStation = currentCapacityOfTheStation;
        this.ipAddress = ipAddress;
        this.level1Factory = level1Factory;
        this.level2Object = level2Object;
        this.level3Zone = level3Zone;
    }
}
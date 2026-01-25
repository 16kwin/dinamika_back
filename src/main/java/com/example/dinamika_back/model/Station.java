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

    // Новые поля
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "fullness")
    private Integer fullness = 0;

    @Column(name = "has_errors", nullable = false)
    private Boolean hasErrors = false;

    @Column(name = "issued")
    private Integer issued = 0;

    @Column(name = "issued_over_norm")
    private Integer issuedOverNorm = 0;

    @Column(name = "finished_parts")
    private Integer finishedParts = 0;

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
                   Integer currentCapacityOfTheStation, String ipAddress, 
                   Boolean isEnabled, Integer capacity, Integer fullness, 
                   Boolean hasErrors, Integer issued, Integer issuedOverNorm, 
                   Integer finishedParts, Location level1Factory, 
                   Location level2Object, Location level3Zone) {
        this.stationName = stationName;
        this.stationModel = stationModel;
        this.serialNumberOfTheStation = serialNumberOfTheStation;
        this.currentCapacityOfTheStation = currentCapacityOfTheStation;
        this.ipAddress = ipAddress;
        this.isEnabled = isEnabled;
        this.capacity = capacity;
        this.fullness = fullness;
        this.hasErrors = hasErrors;
        this.issued = issued;
        this.issuedOverNorm = issuedOverNorm;
        this.finishedParts = finishedParts;
        this.level1Factory = level1Factory;
        this.level2Object = level2Object;
        this.level3Zone = level3Zone;
    }
}
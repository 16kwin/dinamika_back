package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "station_position")
@Getter
@Setter
@NoArgsConstructor
public class StationPosition {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;
    
    @Column(name = "station_id", nullable = false)
    private Integer stationId;
    
    @Column(name = "location_id", nullable = false)
    private Integer locationId;
    
    @Column(name = "coord_x", nullable = false)
    private Integer coordX = 0;
    
    @Column(name = "coord_y", nullable = false)
    private Integer coordY = 0;
}
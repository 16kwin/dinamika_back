package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "location_dependency",
       uniqueConstraints = @UniqueConstraint(columnNames = {"child_location_id"}))
@Getter
@Setter
@NoArgsConstructor
public class LocationDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "child_location_id", nullable = false, unique = true)
    private Location childLocation;

    @ManyToOne
    @JoinColumn(name = "parent_location_id", nullable = false)
    private Location parentLocation;

    public LocationDependency(Location childLocation, Location parentLocation) {
        this.childLocation = childLocation;
        this.parentLocation = parentLocation;
    }
}
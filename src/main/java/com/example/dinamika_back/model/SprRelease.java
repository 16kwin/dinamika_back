package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "spr_release")
@Getter
@Setter
@NoArgsConstructor
public class SprRelease {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @Column(name = "name", nullable = false)
    private String name;
}
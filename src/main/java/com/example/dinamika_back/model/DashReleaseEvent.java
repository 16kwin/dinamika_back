package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Лента «Выпуск продукции» на панели «Показатели» (dash_release_event).
 */
@Entity
@Table(name = "dash_release_event")
@Getter
@Setter
@NoArgsConstructor
public class DashReleaseEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "event_at", nullable = false)
    private LocalDateTime eventAt;
}

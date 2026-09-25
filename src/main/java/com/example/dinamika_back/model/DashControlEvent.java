package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Событие ленты контроля качества (dash_control_event). Дата не хранится: момент события =
 * текущая дата − days_ago + time_of_day, поэтому лента всегда «свежая».
 * status: pending — «На контроль», passed — «Контроль пройден», failed — «Контроль не пройден».
 */
@Entity
@Table(name = "dash_control_event")
@Getter
@Setter
@NoArgsConstructor
public class DashControlEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "department", nullable = false, length = 128)
    private String department;

    @Column(name = "executor", nullable = false)
    private String executor;

    @Column(name = "controller", nullable = false)
    private String controller;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "days_ago", nullable = false)
    private Integer daysAgo;

    @Column(name = "time_of_day", nullable = false)
    private LocalTime timeOfDay;
}

package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Событие «Экрана событий текущего дня» (dash_day_event): source — источник ленты
 * (operator / control / release / overpriced), done=false — колонка «В работе», true — «Завершено»,
 * tone — цвет плашки статуса. Момент события = текущая дата + time_of_day.
 */
@Entity
@Table(name = "dash_day_event")
@Getter
@Setter
@NoArgsConstructor
public class DashDayEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "source", nullable = false, length = 16)
    private String source;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "person", nullable = false, length = 128)
    private String person;

    @Column(name = "status_label", nullable = false, length = 64)
    private String statusLabel;

    @Column(name = "tone", nullable = false, length = 16)
    private String tone;

    @Column(name = "done", nullable = false)
    private Boolean done;

    @Column(name = "time_of_day", nullable = false)
    private LocalTime timeOfDay;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}

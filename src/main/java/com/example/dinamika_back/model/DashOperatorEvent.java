package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Лента панели оператора: kind='order' — «Заказы на поставку», kind='event' — «Экран событий»
 * (dash_operator_event).
 */
@Entity
@Table(name = "dash_operator_event")
@Getter
@Setter
@NoArgsConstructor
public class DashOperatorEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "kind", nullable = false, length = 16)
    private String kind;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "event_at", nullable = false)
    private LocalDateTime eventAt;

    @Column(name = "done", nullable = false)
    private Boolean done;
}

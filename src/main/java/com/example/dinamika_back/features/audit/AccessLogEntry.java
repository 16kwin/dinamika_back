package com.example.dinamika_back.features.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * Запись журнала доступа {@code sys_access_log} (V24, раздел 10.2 проекта): кто пользовался видео, выгрузками,
 * исходниками 3D, адресами камер и исходящими запросами. Только добавление: изменить или удалить запись приложение
 * не может ({@code @Immutable}), база — тоже (триггеры V24). Самих данных и паролей в записи нет.
 */
@Entity
@Immutable
@Table(name = "sys_access_log")
@Getter
@Setter
@NoArgsConstructor
public class AccessLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    /** Тип события — {@code AccessAudit.Event} */
    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "feature_key", nullable = false, length = 64)
    private String featureKey;

    @Column(name = "actor", nullable = false)
    private String actor;

    @Column(name = "actor_role", length = 64)
    private String actorRole;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    /** Id записи, файла или камеры; заголовок выгрузки */
    @Column(name = "object_ref")
    private String objectRef;

    /** JSON: размер, число строк, хост исходящего запроса — без паролей и самих данных */
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;
}

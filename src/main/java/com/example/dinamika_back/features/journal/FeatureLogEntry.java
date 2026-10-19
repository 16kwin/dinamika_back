package com.example.dinamika_back.features.journal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/**
 * Запись журнала профиля {@code sys_feature_log} (V24): только добавление, строки связаны HMAC-цепочкой
 * ({@link JournalHmac}). Пишет только {@link FeatureJournal}: номер и время задаются до вставки, потому что входят
 * в хэш строки. Изменить или удалить запись приложение не может ({@code @Immutable}), база — тоже (триггеры V24).
 */
@Entity
@Immutable
@Table(name = "sys_feature_log")
@Getter
@Setter
@NoArgsConstructor
public class FeatureLogEntry implements Persistable<Long> {

    /** Типы событий журнала профиля: перечень закрыт CHECK в V24, новый тип — осознанная миграция */
    public enum Type {
        STARTUP, CEILING_CHANGED, SWITCH, TAMPER, PURGE, RETENTION, CREDENTIALS_PURGED, USER_CREATED,
        REPORT_VIEWED, REPORT_PRINTED, REPORT_EXPORTED, PROBE_RUN, DENIED_SUMMARY, OUTBOUND_BLOCKED
    }

    /** Номер из последовательности: берётся до вставки, входит в хэш строки */
    @Id
    @Column(name = "id")
    private Long id;

    /** Время с точностью до миллисекунд: столько же хранит хэш, столько же вернёт база */
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "feature_key", length = 64)
    private String featureKey;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "preset", length = 32)
    private String preset;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "ticket", length = 500)
    private String ticket;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "actor", nullable = false)
    private String actor;

    @Column(name = "actor_role", length = 64)
    private String actorRole;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "host_name")
    private String hostName;

    @Column(name = "app_version", length = 64)
    private String appVersion;

    @Column(name = "config_sha256", length = 64)
    private String configSha256;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "prev_hash", length = 64)
    private String prevHash;

    @Column(name = "row_hash", nullable = false, length = 64)
    private String rowHash;

    @Column(name = "key_id", nullable = false, length = 16)
    private String keyId;

    /** Запись создаётся только вставкой: номер задан заранее, без SELECT перед INSERT */
    @Transient
    private boolean fresh;

    @Override
    public boolean isNew() {
        return fresh;
    }
}

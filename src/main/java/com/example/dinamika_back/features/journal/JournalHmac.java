package com.example.dinamika_back.features.journal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Хэш строки журнала профиля (раздел 10.1 проекта): {@code row_hash = HMAC-SHA256(ключ, prev_hash || канонический
 * JSON строки)}. Ключ {@code awms.journal.hmac-key} лежит вне БД (файл секретов, пакет B10), поэтому администратор
 * БД, поправив строку, не пересчитает цепочку.
 *
 * <p>Пока ключа на установке нет (до B10), цепочка считается SHA-256 с идентификатором ключа {@code none}: правку
 * строки она выявляет, а подделку с пересчётом цепочки администратором БД — нет. Отчёт пишет об этом прямо.
 * Строка без ключа после строки с ключом — нарушение: иначе подмену можно было бы пересчитать без ключа.
 *
 * <p>Канонический JSON — поля строки в постоянном порядке, время — с точностью до миллисекунд.
 */
public final class JournalHmac {

    /** Идентификатор цепочки без ключа */
    public static final String NO_KEY = "none";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private final byte[] key;
    private final String keyId;

    /** @param key ключ HMAC из файла секретов; пусто или null — цепочка без ключа */
    public JournalHmac(String key) {
        if (key == null || key.isBlank()) {
            this.key = null;
            this.keyId = NO_KEY;
        } else {
            this.key = key.trim().getBytes(StandardCharsets.UTF_8);
            // Идентификатор — по хэшу ключа: сам ключ из него не восстановить, а смена ключа видна в журнале
            this.keyId = "k" + sha256Hex(("awms-journal-key-id:" + key.trim()).getBytes(StandardCharsets.UTF_8))
                    .substring(0, 12);
        }
    }

    /** Есть ли ключ HMAC (иначе — SHA-256 без ключа) */
    public boolean keyed() {
        return key != null;
    }

    /** Идентификатор текущего ключа или {@link #NO_KEY} */
    public String keyId() {
        return keyId;
    }

    /** Хэш строки текущим ключом (или без ключа) */
    public String rowHash(FeatureLogEntry entry) {
        return hash(keyed() ? keyId : NO_KEY, entry);
    }

    /**
     * Хэш строки ключом с данным идентификатором; null — такого ключа на установке нет (строку не проверить).
     * Строку без ключа ({@link #NO_KEY}) можно проверить всегда
     */
    public String hash(String entryKeyId, FeatureLogEntry entry) {
        byte[] data = (nz(entry.getPrevHash()) + canonical(entry)).getBytes(StandardCharsets.UTF_8);
        if (NO_KEY.equals(entryKeyId)) return sha256Hex(data);
        if (key == null || !keyId.equals(entryKeyId)) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 недоступен", e);
        }
    }

    /** Канонический JSON строки: все поля, кроме row_hash и prev_hash (он стоит перед JSON), в постоянном порядке */
    static String canonical(FeatureLogEntry e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", e.getId());
        row.put("occurred_at", e.getOccurredAt() == null ? null : TIME.format(e.getOccurredAt()));
        row.put("event_type", e.getEventType());
        row.put("feature_key", e.getFeatureKey());
        row.put("old_value", e.getOldValue());
        row.put("new_value", e.getNewValue());
        row.put("preset", e.getPreset());
        row.put("fingerprint", e.getFingerprint());
        row.put("ticket", e.getTicket());
        row.put("reason", e.getReason());
        row.put("actor", e.getActor());
        row.put("actor_role", e.getActorRole());
        row.put("client_ip", e.getClientIp());
        row.put("host_name", e.getHostName());
        row.put("app_version", e.getAppVersion());
        row.put("config_sha256", e.getConfigSha256());
        row.put("details", e.getDetails());
        row.put("key_id", e.getKeyId());
        try {
            return JSON.writeValueAsString(row);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Время записи журнала: с точностью до миллисекунд, как в хэше */
    public static LocalDateTime truncate(LocalDateTime time) {
        return time.withNano(time.getNano() / 1_000_000 * 1_000_000);
    }

    public static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}

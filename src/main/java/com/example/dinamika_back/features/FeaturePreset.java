package com.example.dinamika_back.features;

import java.util.Locale;

/**
 * Пресеты потолка установки (ключ {@code awms.features.preset}). Нет ключа или файла потолка — {@link #ISOLATED}.
 * Состав каждого пресета задан в реестре {@link Feature} (столбец I/S/F таблицы 4.2 проекта).
 */
public enum FeaturePreset {

    /** «Изолированный минимум»: только ядро учёта (fail-closed, умолчание без файла) */
    ISOLATED("isolated", "Изолированный минимум"),
    /** «Стандарт»: рекомендуемый для закрытого контура */
    STANDARD("standard", "Стандарт"),
    /** «Расширенный»: стенд, демо */
    FULL("full", "Расширенный");

    private final String key;
    private final String title;

    FeaturePreset(String key, String title) {
        this.key = key;
        this.title = title;
    }

    /** Имя в файле потолка: isolated, standard, full */
    public String key() {
        return key;
    }

    public String title() {
        return title;
    }

    /** Пресет по имени из файла (без учёта регистра и пробелов по краям) или null, если такого нет */
    public static FeaturePreset fromKey(String value) {
        if (value == null) return null;
        String wanted = value.trim().toLowerCase(Locale.ROOT);
        for (FeaturePreset preset : values()) {
            if (preset.key.equals(wanted)) return preset;
        }
        return null;
    }
}

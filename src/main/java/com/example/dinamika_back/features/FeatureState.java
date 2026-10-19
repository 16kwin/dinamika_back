package com.example.dinamika_back.features;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Состояние одной функции в снимке.
 *
 * @param feature    функция
 * @param enabled    итоговое состояние (с учётом зависимостей)
 * @param reason     причина состояния
 * @param dependency ключ выключенной зависимости для {@link Reason#DEPENDENCY}, иначе null
 * @param roles      роли из {@code awms.features.<key>.roles} (без префикса ROLE_); пусто — все вошедшие
 * @param origin     источник значения: «файл awms-features.properties:5», «пресет full», «jar: умолчание»
 */
public record FeatureState(Feature feature, boolean enabled, Reason reason, String dependency, Set<String> roles,
                           String origin) {

    /** Причина состояния функции */
    public enum Reason {
        /** По пресету потолка */
        PRESET,
        /** Явным ключом {@code awms.features.<key>.enabled} */
        PROPERTY,
        /** Выключена зависимость: родитель или функция из requires */
        DEPENDENCY,
        /** Класса-маркера модуля нет в jar (сборка без модуля, B9) */
        ABSENT_IN_BUILD,
        /** Отсутствует в продукте (раздел 4.3) */
        ABSENT,
        /** Выключена переключателем администратора (этап 2) */
        SWITCH,
        /** Состояние переключателя расходится с журналом (этап 2) */
        TAMPER
    }

    public FeatureState {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(reason, "reason");
        // По алфавиту: роли входят в отпечаток профиля
        roles = Collections.unmodifiableSortedSet(roles == null ? new TreeSet<>() : new TreeSet<>(roles));
    }

    public String key() {
        return feature.key();
    }

    /** Код причины для API и журнала: {@code PRESET}, {@code DEPENDENCY:video} */
    public String reasonCode() {
        return reason == Reason.DEPENDENCY ? "DEPENDENCY:" + dependency : reason.name();
    }

    /** Причина по-русски для лога, отчёта и {@code --debug} */
    public String reasonText() {
        String state = enabled ? "включена" : "выключена";
        return switch (reason) {
            case PRESET -> state + " пресетом";
            case PROPERTY -> state + " ключом (" + origin + ")";
            case DEPENDENCY -> "выключена: выключена функция " + dependency + ", от которой она зависит";
            case ABSENT_IN_BUILD -> "отсутствует в сборке";
            case ABSENT -> "отсутствует в продукте";
            case SWITCH -> "выключена администратором";
            case TAMPER -> "выключена: состояние изменено в обход приложения";
        };
    }

    FeatureState with(boolean newEnabled, Reason newReason, String newDependency) {
        return new FeatureState(feature, newEnabled, newReason, newDependency, roles, origin);
    }
}

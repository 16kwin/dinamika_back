package com.example.dinamika_back.features;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Бин создаётся только когда потолок установки разрешает функцию (уровень L3: нет эндпоинтов, потоков, процессов).
 * {@code enabled = false} — наоборот, бин-заглушка «когда функции нет» (например, {@code NoopLiveUpdates}).
 *
 * <p>Смотрит только на потолок: переключатели администратора (этап 2) бины не создают и не уничтожают.
 * Причина решения видна в отчёте условий ({@code --debug}).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnFeatureCondition.class)
public @interface ConditionalOnFeature {

    /** Функция из реестра */
    Feature value();

    /** true — бин нужен при разрешённой функции; false — заглушка при выключенной */
    boolean enabled() default true;
}

package com.example.dinamika_back.features.report;

import org.springframework.context.annotation.Configuration;

/**
 * Контекст команд обслуживания ({@link FeatureCli}): без автоконфигурации, веб-сервера и БД. Команда
 * {@code print-features} берёт из него только окружение — путь к файлу потолка по умолчанию определяется так же,
 * как у службы ({@code AWMS_FEATURES_FILE} или {@code ./config/awms-features.properties}). Команды, которым нужна БД
 * ({@code create-user}, пакет B10), подключают свои настройки к этому классу.
 *
 * <p>Класс не сканируется вместе с приложением как источник бинов: бинов в нём нет, а сканирование компонентов
 * он не включает.
 */
@Configuration(proxyBeanMethods = false)
public class FeatureCliConfiguration {
}

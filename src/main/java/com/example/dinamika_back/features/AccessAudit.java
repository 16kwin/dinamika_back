package com.example.dinamika_back.features;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Журнал доступа (раздел 10.2 проекта): кто пользовался данными, которые безопасник считает чувствительными —
 * видео сотрудников, выгрузки, исходники 3D, адреса камер, исходящие запросы.
 *
 * <p>Автора и адрес клиента реализация берёт сама (контекст безопасности и текущий запрос), поэтому сторожа
 * и сервисы передают только событие. До пакета B6 работала пустая реализация {@link Noop}: B2 и B3 писали события,
 * не дожидаясь таблицы {@code sys_access_log}. Реализация B6 ({@code JpaAccessAudit}) помечена {@code @Primary}.
 *
 * <p>Сбой записи: по умолчанию реализация его не бросает (предупреждение в лог, действие выполняется — как до
 * выключателей); с {@code awms.journal.required=true} в потолке бросает, и сторож отказывает в действии.
 */
public interface AccessAudit {

    /** Типы событий журнала доступа. Перечень — в коде, в таблице CHECK нет: новый тип не требует миграции */
    enum Event {
        /** Отдача {@code GET /api/video/files/*}{@code /content} */
        VIDEO_VIEWED,
        /** Подписка на живой поток */
        VIDEO_LIVE_OPENED,
        /** Скачивание файла или клипа видео */
        VIDEO_DOWNLOADED,
        /** Запись по событию станции */
        VIDEO_RECORDING_STARTED,
        /** Смена адреса камеры (адреса без паролей «было → стало») */
        CAMERA_CHANGED,
        /** Выгрузка PDF/Excel/Word и печать: заголовок и число строк, без данных */
        EXPORT,
        /** Скачивание исходника 3D-модели */
        MODEL3D_SOURCE,
        /** Разрешённый исходящий HTTP: хост, путь, статус, размер */
        OUTBOUND_SENT
    }

    /**
     * Записать событие.
     *
     * @param event     тип события
     * @param feature   функция, к которой относится событие
     * @param objectRef id записи, файла или камеры; заголовок выгрузки (может быть null)
     * @param details   подробности без паролей и самих данных: размер, число строк, хост (может быть null)
     */
    void record(Event event, Feature feature, String objectRef, Map<String, ?> details);

    default void record(Event event, Feature feature, String objectRef) {
        record(event, feature, objectRef, null);
    }

    /**
     * Исходящий запрос заблокирован сторожем ({@code OutboundGuard}): запись {@code OUTBOUND_BLOCKED} в журнал
     * профиля, а не в журнал доступа — блокировка говорит о настройке установки, а не об использовании данных.
     * Ошибку записи реализация не бросает: запрос и так не уходит, сторож отвечает отказом сам.
     *
     * @param feature функция клиента или null (клиент без функции)
     * @param target  адрес без учётных данных и строки запроса
     * @param reason  причина блокировки
     */
    default void outboundBlocked(Feature feature, String target, String reason) {
        // пустая реализация: журнала профиля нет
    }

    /**
     * Пустая реализация: события никуда не пишутся. В службе вместо неё работает JpaAccessAudit ({@code @Primary},
     * B6); эта остаётся для срезов контекста без БД и для тестов модулей
     */
    @Component
    class Noop implements AccessAudit {
        @Override
        public void record(Event event, Feature feature, String objectRef, Map<String, ?> details) {
            // журнал доступа ведёт JpaAccessAudit
        }
    }
}

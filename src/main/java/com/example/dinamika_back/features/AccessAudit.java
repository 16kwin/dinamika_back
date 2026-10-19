package com.example.dinamika_back.features;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Журнал доступа (раздел 10.2 проекта): кто пользовался данными, которые безопасник считает чувствительными —
 * видео сотрудников, выгрузки, исходники 3D, адреса камер, исходящие запросы.
 *
 * <p>Автора и адрес клиента реализация берёт сама (контекст безопасности и текущий запрос), поэтому сторожа
 * и сервисы передают только событие. До пакета B6 работает пустая реализация {@link Noop}: B2 и B3 пишут события,
 * не дожидаясь таблицы {@code sys_access_log}. Реализация B6 помечается {@code @Primary}.
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

    /** Пустая реализация до пакета B6: события никуда не пишутся */
    @Component
    class Noop implements AccessAudit {
        @Override
        public void record(Event event, Feature feature, String objectRef, Map<String, ?> details) {
            // журнала доступа ещё нет (B6)
        }
    }
}

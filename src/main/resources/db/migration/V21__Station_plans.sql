-- V21__Station_plans.sql
-- Интерактивная карта станций: план цеха (картинка) и расстановка станций на нём.
--
-- Одна запись station_plans на цех. Если картинки нет (image_* = NULL), на карте рисуется
-- автоматическая схема цеха, а позиции станций относятся к ней.
-- Позиции хранятся в долях 0..1 от ширины и высоты плана: замена картинки на другую
-- с теми же пропорциями сохраняет расстановку.
-- version — оптимистическая блокировка: сохранение расстановки с устаревшей версией получает 409.

CREATE TABLE IF NOT EXISTS station_plans (
    id           BIGSERIAL    PRIMARY KEY,
    workshop_id  BIGINT       NOT NULL UNIQUE REFERENCES workshops(id) ON DELETE CASCADE,
    -- Путь к файлу относительно каталога планов (station-plans.storage-dir): {workshopId}/{uuid}.{ext}
    image_path   VARCHAR(500),
    image_mime   VARCHAR(50),
    image_width  INTEGER,
    image_height INTEGER,
    version      BIGINT       NOT NULL DEFAULT 0,
    updated_by   VARCHAR(255),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Картинка задаётся целиком или отсутствует
    CONSTRAINT station_plans_image_chk CHECK (
        (image_path IS NULL AND image_mime IS NULL AND image_width IS NULL AND image_height IS NULL)
        OR (image_path IS NOT NULL AND image_mime IS NOT NULL AND image_width > 0 AND image_height > 0)
    )
);

-- Позиция станции на плане: точка «пола» станции в долях плана
CREATE TABLE IF NOT EXISTS station_plan_positions (
    plan_id    BIGINT           NOT NULL REFERENCES station_plans(id) ON DELETE CASCADE,
    station_id BIGINT           NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
    x          DOUBLE PRECISION NOT NULL CHECK (x >= 0 AND x <= 1),
    y          DOUBLE PRECISION NOT NULL CHECK (y >= 0 AND y <= 1),
    updated_at TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (plan_id, station_id)
);

CREATE INDEX IF NOT EXISTS idx_station_plan_positions_station ON station_plan_positions(station_id);

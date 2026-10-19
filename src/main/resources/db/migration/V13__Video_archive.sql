-- V13__Video_archive.sql
-- Видеоархив: камеры станций, записи по событиям (журнал) и файлы записей по камерам.
--
-- Запись идёт по паре событий: «начало» запускает FFmpeg на камерах станции, «окончание»
-- останавливает его. Одна строка video_record — одно событие, по строке video_record_file
-- на каждую камеру, которая его снимала.

-- Камера станции: адрес, тип и строка подключения (RTSP)
CREATE TABLE IF NOT EXISTS video_camera (
    id          BIGSERIAL    PRIMARY KEY,
    uid         UUID         NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    name        VARCHAR(64)  NOT NULL,
    ip          VARCHAR(64)  NOT NULL,
    mac         VARCHAR(32),
    -- 'station' — камера станции, 'overview' — обзорная камера
    camera_type VARCHAR(16)  NOT NULL,
    -- Строка подключения целиком, с логином и паролем: rtsp://login:password@ip:554/...
    -- Для разработки без камер допускается 'test:' — тестовый сигнал FFmpeg
    rtsp_url    VARCHAR(512) NOT NULL,
    station_id  BIGINT       REFERENCES stations(id) ON DELETE SET NULL,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT video_camera_type_chk CHECK (camera_type IN ('station', 'overview'))
);

-- Запись по событию — строка журнала видеоархива
CREATE TABLE IF NOT EXISTS video_record (
    id               BIGSERIAL   PRIMARY KEY,
    uid              UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    -- Код события: 'authority' — авторизация, 'issue' — выдача
    event_type       VARCHAR(32) NOT NULL,
    station_id       BIGINT      REFERENCES stations(id) ON DELETE SET NULL,
    -- 'recording' — идёт запись, 'done' — готово, 'failed' — ни одна камера не записала
    status           VARCHAR(16) NOT NULL,
    started_at       TIMESTAMP   NOT NULL,
    finished_at      TIMESTAMP,
    duration_seconds NUMERIC(10,2),
    error_message    TEXT,
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT video_record_status_chk CHECK (status IN ('recording', 'done', 'failed'))
);

-- Файл записи одной камеры. Сведения о камере копируются: журнал не должен меняться,
-- если камеру позже переименуют или удалят
CREATE TABLE IF NOT EXISTS video_record_file (
    id               BIGSERIAL     PRIMARY KEY,
    record_id        BIGINT        NOT NULL REFERENCES video_record(id) ON DELETE CASCADE,
    camera_id        BIGINT        REFERENCES video_camera(id) ON DELETE SET NULL,
    camera_name      VARCHAR(64)   NOT NULL,
    camera_ip        VARCHAR(64)   NOT NULL,
    camera_type      VARCHAR(16)   NOT NULL,
    -- Путь относительно каталога видеоархива (video.storage-dir)
    file_path        VARCHAR(1024) NOT NULL,
    file_size        BIGINT,
    duration_seconds NUMERIC(10,2),
    status           VARCHAR(16)   NOT NULL,
    error_message    TEXT,
    CONSTRAINT video_record_file_status_chk CHECK (status IN ('recording', 'done', 'failed'))
);

CREATE INDEX IF NOT EXISTS idx_video_record_started ON video_record(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_video_record_station ON video_record(station_id);
CREATE INDEX IF NOT EXISTS idx_video_record_file_record ON video_record_file(record_id);

-- Камеры стенда: станционная HiWatch IPC-T020(C) и обзорная HiWatch IPC-B020(C).
-- Адреса и MAC сверены с работающим стендом. Оба потока камер — H.265, 1920×1080, 25 к/с.
-- Пароль в строку подключения вводится на странице
-- видеоархива: в миграцию он не попадает, потому что миграция применяется и на рабочей базе.
INSERT INTO video_camera (name, ip, mac, camera_type, rtsp_url, station_id, sort_order)
SELECT v.name, v.ip, v.mac, v.camera_type, v.rtsp_url,
       (SELECT id FROM stations WHERE uid = 'ST-001'), v.sort_order
FROM (VALUES
    ('Cam01', '192.168.1.64', '08:3B:C1:FF:F7:07', 'station',
     'rtsp://admin@192.168.1.64:554/ISAPI/Streaming/Channels/101', 1),
    ('Cam02', '192.168.1.70', '04:EE:CD:93:6F:26', 'overview',
     'rtsp://admin@192.168.1.70:554/ISAPI/Streaming/Channels/101', 2)
) AS v(name, ip, mac, camera_type, rtsp_url, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM video_camera);

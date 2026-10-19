-- V22__Nomenclature_models.sql
-- 3D-модели номенклатуры: исходный файл из САПР и облегчённая копия GLB для просмотра в браузере.
--
-- Файлы лежат не в публичном uploads/, а в каталоге nomenclature.models-dir и отдаются
-- только после входа: {material_uid}/{uid}.{расширение} и {material_uid}/{uid}.view.glb.

CREATE TABLE IF NOT EXISTS spr_material_models (
    uid           UUID         PRIMARY KEY,
    material_uid  UUID         NOT NULL REFERENCES spr_material(uid) ON DELETE CASCADE,
    -- Исходник, путь относительно каталога моделей
    file_path     TEXT         NOT NULL,
    original_name TEXT         NOT NULL,
    -- glb, gltf, step, iges, vrml, stl, obj, 3mf, ply, x3d
    format        VARCHAR(16)  NOT NULL,
    file_size     BIGINT       NOT NULL,
    -- Копия GLB для просмотра; NULL — просмотр открывает исходник
    view_path     TEXT,
    view_size     BIGINT,
    triangles     BIGINT,
    author        VARCHAR(255),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_spr_material_models_material
    ON spr_material_models (material_uid, created_at);

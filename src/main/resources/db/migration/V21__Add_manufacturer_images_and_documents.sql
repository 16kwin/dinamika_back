BEGIN;

CREATE TABLE IF NOT EXISTS spr_manufacturer_images (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    manufacturer_uid UUID NOT NULL REFERENCES spr_manufacturer(uid) ON DELETE CASCADE,
    file_path VARCHAR(500) NOT NULL,
    original_name VARCHAR(500),
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_manufacturer_images_manufacturer ON spr_manufacturer_images(manufacturer_uid);

CREATE TABLE IF NOT EXISTS spr_manufacturer_documents (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    manufacturer_uid UUID NOT NULL REFERENCES spr_manufacturer(uid) ON DELETE CASCADE,
    document_name VARCHAR(500),
    file_path VARCHAR(1000),
    original_name VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_manufacturer_documents_manufacturer ON spr_manufacturer_documents(manufacturer_uid);

COMMIT;
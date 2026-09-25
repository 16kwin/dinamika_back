-- V6__Template_event_log.sql
-- История изменений шаблона пополнения (doc_pattern) и его ячеек (reg_cells)

BEGIN;

CREATE TABLE IF NOT EXISTS template_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_uid UUID REFERENCES doc_pattern(uid) ON DELETE CASCADE,
    cell_uid UUID REFERENCES reg_cells(uid) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_template_event_log_template
    ON template_event_log(template_uid);
CREATE INDEX IF NOT EXISTS idx_template_event_log_cell
    ON template_event_log(cell_uid);
CREATE INDEX IF NOT EXISTS idx_template_event_log_created
    ON template_event_log(created_at DESC);

COMMIT;
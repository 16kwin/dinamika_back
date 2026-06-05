ALTER TABLE reg_suppliers 
ADD COLUMN IF NOT EXISTS supply_date timestamp,
ADD COLUMN IF NOT EXISTS document_name text,
ADD COLUMN IF NOT EXISTS file_path text,
ADD COLUMN IF NOT EXISTS original_name text;
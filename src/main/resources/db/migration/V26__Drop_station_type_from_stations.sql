-- V20__Drop_station_type_from_stations.sql

BEGIN;

ALTER TABLE IF EXISTS public.stations
    DROP COLUMN IF EXISTS station_type;

COMMIT;
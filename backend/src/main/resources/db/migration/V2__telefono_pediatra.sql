-- =============================================================================
-- V2: teléfono del pediatra (ítem 2.4, ronda 3)
-- =============================================================================
-- Va aparte de V1 para que las bases que ya existían (marcadas en la versión 1
-- por el baseline) también reciban la columna. IF NOT EXISTS cubre las que ya
-- la crearon con ddl-auto=update antes de Flyway.
-- =============================================================================

alter table usuario add column if not exists telefono_pediatra varchar(20);

-- Homologación ETIC PdM Android -> ETIC Online.
-- MySQL 8.4. Incremental, idempotente y no destructivo. No ejecutar automáticamente.

SET @etic_schema := DATABASE();

SET @etic_sql := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @etic_schema
      AND TABLE_NAME = 'datos_reporte'
      AND COLUMN_NAME = 'desglose_areas_inspeccionadas'
  ),
  'SELECT 1',
  'ALTER TABLE datos_reporte ADD COLUMN desglose_areas_inspeccionadas TEXT NULL'
);
PREPARE etic_stmt FROM @etic_sql;
EXECUTE etic_stmt;
DEALLOCATE PREPARE etic_stmt;

SET @etic_sql := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @etic_schema
      AND TABLE_NAME = 'datos_reporte'
      AND COLUMN_NAME = 'incluir_desglose_areas'
  ),
  'SELECT 1',
  'ALTER TABLE datos_reporte ADD COLUMN incluir_desglose_areas TINYINT(1) NULL'
);
PREPARE etic_stmt FROM @etic_sql;
EXECUTE etic_stmt;
DEALLOCATE PREPARE etic_stmt;

-- Orden del árbol: NULL histórico; no modifica jerarquía, claves ni valores existentes.
SET @etic_sql := IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @etic_schema
      AND TABLE_NAME = 'ubicaciones'
      AND COLUMN_NAME = 'Orden_Arbol'
  ),
  'SELECT 1',
  'ALTER TABLE ubicaciones ADD COLUMN Orden_Arbol INT NULL'
);
PREPARE etic_stmt FROM @etic_sql;
EXECUTE etic_stmt;
DEALLOCATE PREPARE etic_stmt;

SET @etic_sql := NULL;
SET @etic_schema := NULL;

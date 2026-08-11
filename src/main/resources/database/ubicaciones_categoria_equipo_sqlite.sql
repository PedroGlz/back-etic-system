PRAGMA foreign_keys = ON;

ALTER TABLE ubicaciones
  ADD COLUMN Id_Categoria_Equipo TEXT NULL
  REFERENCES categorias_equipos (id_categoria_equipo);

CREATE INDEX IF NOT EXISTS IX_Ubicaciones_Categoria_Equipo
  ON ubicaciones (Id_Categoria_Equipo);

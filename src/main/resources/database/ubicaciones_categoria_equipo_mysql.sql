SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET CHARACTER SET utf8mb4;

ALTER TABLE ubicaciones
  ADD COLUMN Id_Categoria_Equipo CHAR(38)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL AFTER Es_Equipo,
  ADD INDEX IX_Ubicaciones_Categoria_Equipo (Id_Categoria_Equipo),
  ADD CONSTRAINT FK_Ubicaciones_Categoria_Equipo
    FOREIGN KEY (Id_Categoria_Equipo)
    REFERENCES categorias_equipos (id_categoria_equipo);

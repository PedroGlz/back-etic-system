SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS categorias_equipos (
  id_categoria_equipo CHAR(38) NOT NULL,
  nombre_categoria TEXT NOT NULL,
  Estatus ENUM('Activo', 'Inactivo') DEFAULT 'Activo',
  Creado_Por CHAR(38) DEFAULT NULL,
  Fecha_Creacion DATETIME DEFAULT NULL,
  Modificado_Por CHAR(38) DEFAULT NULL,
  Fecha_Mod DATETIME DEFAULT NULL,
  Tipo_Registro VARCHAR(30) NOT NULL DEFAULT 'MAESTRO',
  Id_Registro_Origen CHAR(38) DEFAULT NULL,
  Id_Inspeccion CHAR(38) DEFAULT NULL,
  Es_Historico ENUM('SI', 'NO') NOT NULL DEFAULT 'NO',
  Origen_Captura VARCHAR(30) NOT NULL DEFAULT 'ONLINE',
  Comentario_Revision TEXT,
  Revisado_Por CHAR(38) DEFAULT NULL,
  Fecha_Revision DATETIME DEFAULT NULL,
  PRIMARY KEY (id_categoria_equipo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

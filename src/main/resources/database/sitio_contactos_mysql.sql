SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS sitio_contactos (
    Id_Sitio_Contacto VARCHAR(64) NOT NULL,
    Id_Sitio CHAR(36) NOT NULL,
    Nombre VARCHAR(200) NULL,
    Puesto VARCHAR(200) NULL,
    Estatus VARCHAR(20) NOT NULL DEFAULT 'Activo',
    Orden INT NOT NULL DEFAULT 0,
    PRIMARY KEY (Id_Sitio_Contacto),
    INDEX IX_Sitio_Contactos_Sitio (Id_Sitio),
    CONSTRAINT FK_Sitio_Contactos_Sitio FOREIGN KEY (Id_Sitio) REFERENCES sitios (Id_Sitio)
);

ALTER TABLE sitio_contactos
    MODIFY COLUMN Id_Sitio_Contacto VARCHAR(64) NOT NULL;

INSERT INTO sitio_contactos (Id_Sitio_Contacto, Id_Sitio, Nombre, Puesto, Estatus, Orden)
SELECT UPPER(UUID()), Id_Sitio, Contacto_1, Puesto_Contacto_1, 'Activo', 1
FROM sitios s
WHERE (COALESCE(Contacto_1, '') <> '' OR COALESCE(Puesto_Contacto_1, '') <> '')
  AND NOT EXISTS (SELECT 1 FROM sitio_contactos sc WHERE sc.Id_Sitio = s.Id_Sitio AND sc.Orden = 1);

INSERT INTO sitio_contactos (Id_Sitio_Contacto, Id_Sitio, Nombre, Puesto, Estatus, Orden)
SELECT UPPER(UUID()), Id_Sitio, Contacto_2, Puesto_Contacto_2, 'Activo', 2
FROM sitios s
WHERE (COALESCE(Contacto_2, '') <> '' OR COALESCE(Puesto_Contacto_2, '') <> '')
  AND NOT EXISTS (SELECT 1 FROM sitio_contactos sc WHERE sc.Id_Sitio = s.Id_Sitio AND sc.Orden = 2);

INSERT INTO sitio_contactos (Id_Sitio_Contacto, Id_Sitio, Nombre, Puesto, Estatus, Orden)
SELECT UPPER(UUID()), Id_Sitio, Contacto_3, Puesto_Contacto_3, 'Activo', 3
FROM sitios s
WHERE (COALESCE(Contacto_3, '') <> '' OR COALESCE(Puesto_Contacto_3, '') <> '')
  AND NOT EXISTS (SELECT 1 FROM sitio_contactos sc WHERE sc.Id_Sitio = s.Id_Sitio AND sc.Orden = 3);

ALTER TABLE sitios
    DROP COLUMN Contacto_1,
    DROP COLUMN Puesto_Contacto_1,
    DROP COLUMN Contacto_2,
    DROP COLUMN Puesto_Contacto_2,
    DROP COLUMN Contacto_3,
    DROP COLUMN Puesto_Contacto_3;

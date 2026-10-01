CREATE DATABASE IF NOT EXISTS license_system CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE license_system;

CREATE TABLE IF NOT EXISTS licensed_applications (
  Id_Application CHAR(38) NOT NULL,
  Code VARCHAR(80) NOT NULL,
  Name VARCHAR(150) NOT NULL,
  Package_Name VARCHAR(255) NOT NULL,
  Licensing_Mode ENUM('USER_DEVICE','DEVICE_ONLY') NOT NULL,
  Status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  Created_At DATETIME NOT NULL,
  Updated_At DATETIME NULL,
  Created_By CHAR(38) NULL,
  Updated_By CHAR(38) NULL,
  PRIMARY KEY (Id_Application),
  UNIQUE KEY uq_licensed_applications_code (Code),
  UNIQUE KEY uq_licensed_applications_package (Package_Name),
  KEY idx_licensed_applications_status (Status)
);

CREATE TABLE IF NOT EXISTS licensed_devices (
  Id_Device CHAR(38) NOT NULL,
  Device_UUID VARCHAR(128) NOT NULL,
  Display_Name VARCHAR(150) NULL,
  Manufacturer VARCHAR(100) NULL,
  Model VARCHAR(100) NULL,
  Android_Version VARCHAR(50) NULL,
  Status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  Registered_At DATETIME NOT NULL,
  Last_Validation_At DATETIME NULL,
  Updated_At DATETIME NULL,
  Updated_By CHAR(38) NULL,
  PRIMARY KEY (Id_Device),
  UNIQUE KEY uq_licensed_devices_uuid (Device_UUID),
  KEY idx_licensed_devices_status (Status)
);

CREATE TABLE IF NOT EXISTS licenses (
  Id_License CHAR(38) NOT NULL,
  Id_Application CHAR(38) NOT NULL,
  Id_Device CHAR(38) NOT NULL,
  Id_Usuario CHAR(38) NULL,
  Valid_From DATE NOT NULL,
  Valid_Until DATE NOT NULL,
  Status ENUM('ACTIVE','SUSPENDED','REVOKED','EXPIRED') NOT NULL,
  Created_At DATETIME NOT NULL,
  Updated_At DATETIME NULL,
  Created_By CHAR(38) NULL,
  Updated_By CHAR(38) NULL,
  Active_Identity VARCHAR(220) GENERATED ALWAYS AS (
    CASE WHEN Status = 'ACTIVE'
      THEN CONCAT(Id_Application, '|', Id_Device, '|', COALESCE(Id_Usuario, 'DEVICE_ONLY'))
      ELSE NULL
    END
  ) STORED,
  PRIMARY KEY (Id_License),
  UNIQUE KEY uq_licenses_active_identity (Active_Identity),
  KEY idx_licenses_application (Id_Application),
  KEY idx_licenses_device (Id_Device),
  KEY idx_licenses_user (Id_Usuario),
  KEY idx_licenses_status_validity (Status, Valid_Until),
  CONSTRAINT fk_licenses_application FOREIGN KEY (Id_Application) REFERENCES licensed_applications (Id_Application),
  CONSTRAINT fk_licenses_device FOREIGN KEY (Id_Device) REFERENCES licensed_devices (Id_Device),
  CONSTRAINT chk_licenses_validity CHECK (Valid_Until >= Valid_From)
);

-- Id_Usuario referencia lógicamente usuarios.Id_Usuario. No se crea FK física porque
-- el repositorio no incluye el DDL autoritativo de usuarios para comprobar tipo/collation.
-- Device_UUID es el identificador provisional; la identidad criptográfica Android se añadirá en otra fase.

CREATE TABLE IF NOT EXISTS application_versions (
  Id_Version CHAR(38) NOT NULL,
  Id_Application CHAR(38) NOT NULL,
  Version_Name VARCHAR(80) NOT NULL,
  Version_Code BIGINT NOT NULL,
  Original_File_Name VARCHAR(255) NOT NULL,
  Storage_File_Name VARCHAR(255) NOT NULL,
  Sha256 CHAR(64) NOT NULL,
  File_Size BIGINT NOT NULL,
  Minimum_Android VARCHAR(40) NULL,
  Release_Notes TEXT NULL,
  Mandatory BOOLEAN NOT NULL DEFAULT FALSE,
  Published BOOLEAN NOT NULL DEFAULT FALSE,
  Created_At DATETIME NOT NULL,
  Created_By CHAR(38) NOT NULL,
  Modified_At DATETIME NULL,
  Modified_By CHAR(38) NULL,
  PRIMARY KEY (Id_Version),
  UNIQUE KEY uq_application_versions_code (Id_Application, Version_Code),
  KEY idx_application_versions_published (Id_Application, Published, Version_Code),
  CONSTRAINT fk_application_versions_application FOREIGN KEY (Id_Application) REFERENCES licensed_applications (Id_Application)
);

CREATE TABLE IF NOT EXISTS user_application_access (
  Id_Access CHAR(38) NOT NULL,
  Id_Usuario CHAR(38) NOT NULL,
  Id_Application CHAR(38) NOT NULL,
  Status ENUM('ACTIVE','SUSPENDED','REVOKED') NOT NULL DEFAULT 'ACTIVE',
  Valid_From DATE NOT NULL,
  Valid_Until DATE NULL,
  Created_At DATETIME NOT NULL,
  Created_By CHAR(38) NOT NULL,
  Modified_At DATETIME NULL,
  Modified_By CHAR(38) NULL,
  PRIMARY KEY (Id_Access),
  UNIQUE KEY uq_user_application_access (Id_Usuario, Id_Application),
  KEY idx_user_application_access_application (Id_Application),
  CONSTRAINT fk_user_application_access_application FOREIGN KEY (Id_Application) REFERENCES licensed_applications (Id_Application),
  CONSTRAINT chk_user_application_access_validity CHECK (Valid_Until IS NULL OR Valid_Until >= Valid_From)
);

-- user_application_access.Id_Usuario es referencia lógica a etic_system.usuarios; no hay FK entre bases.

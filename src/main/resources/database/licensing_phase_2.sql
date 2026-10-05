SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET CHARACTER SET utf8mb4;

USE license_system;

DROP PROCEDURE IF EXISTS add_licensing_column;
DELIMITER $$
CREATE PROCEDURE add_licensing_column(IN table_name_value VARCHAR(64), IN column_name_value VARCHAR(64), IN definition_value TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = table_name_value AND COLUMN_NAME = column_name_value
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', table_name_value, '` ADD COLUMN `', column_name_value, '` ', definition_value);
    PREPARE statement_value FROM @ddl;
    EXECUTE statement_value;
    DEALLOCATE PREPARE statement_value;
  END IF;
END$$
DELIMITER ;

ALTER TABLE licensed_devices
  MODIFY COLUMN Device_UUID VARCHAR(128) NULL,
  MODIFY COLUMN Status ENUM('PENDING','ENROLLED','ACTIVE','SUSPENDED','REVOKED','INACTIVE') NOT NULL DEFAULT 'PENDING';

CALL add_licensing_column('licensed_devices', 'Origin', "ENUM('MANUAL','AUTO') NOT NULL DEFAULT 'AUTO' AFTER Device_UUID");
CALL add_licensing_column('licensed_devices', 'Android_ID', 'VARCHAR(255) NULL AFTER Android_Version');
CALL add_licensing_column('licensed_devices', 'Package_Name', 'VARCHAR(255) NULL AFTER Android_ID');
CALL add_licensing_column('licensed_devices', 'App_Version', 'VARCHAR(80) NULL AFTER Package_Name');
CALL add_licensing_column('licensed_devices', 'Public_Key_Algorithm', 'VARCHAR(40) NULL AFTER App_Version');
CALL add_licensing_column('licensed_devices', 'Public_Key', 'TEXT NULL AFTER Public_Key_Algorithm');
CALL add_licensing_column('licensed_devices', 'Public_Key_Fingerprint', 'CHAR(64) NULL AFTER Public_Key');
CALL add_licensing_column('licensed_devices', 'Key_Security_Level', "ENUM('SOFTWARE','TEE','STRONGBOX','UNKNOWN') NOT NULL DEFAULT 'UNKNOWN' AFTER Public_Key_Fingerprint");
CALL add_licensing_column('licensed_devices', 'Attestation_Available', 'BOOLEAN NOT NULL DEFAULT FALSE AFTER Key_Security_Level');
CALL add_licensing_column('licensed_devices', 'Attestation_Verified', 'BOOLEAN NOT NULL DEFAULT FALSE AFTER Attestation_Available');
CALL add_licensing_column('licensed_devices', 'Notes', 'TEXT NULL AFTER Attestation_Verified');
CALL add_licensing_column('licensed_devices', 'Enrolled_At', 'DATETIME NULL AFTER Registered_At');
CALL add_licensing_column('licensed_devices', 'Key_Rotated_At', 'DATETIME NULL AFTER Enrolled_At');

DROP PROCEDURE add_licensing_column;

DROP PROCEDURE IF EXISTS add_licensing_index;
DELIMITER $$
CREATE PROCEDURE add_licensing_index(IN table_name_value VARCHAR(64), IN index_name_value VARCHAR(64), IN ddl_value TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = table_name_value AND INDEX_NAME = index_name_value
  ) THEN
    SET @ddl = ddl_value;
    PREPARE statement_value FROM @ddl;
    EXECUTE statement_value;
    DEALLOCATE PREPARE statement_value;
  END IF;
END$$
DELIMITER ;

CALL add_licensing_index('licensed_devices', 'uq_licensed_devices_public_key_fingerprint',
  'CREATE UNIQUE INDEX uq_licensed_devices_public_key_fingerprint ON licensed_devices (Public_Key_Fingerprint)');
CALL add_licensing_index('licensed_devices', 'idx_licensed_devices_origin_status',
  'CREATE INDEX idx_licensed_devices_origin_status ON licensed_devices (Origin, Status)');
DROP PROCEDURE add_licensing_index;

CREATE TABLE IF NOT EXISTS device_enrollment_codes (
  Id_Enrollment CHAR(38) NOT NULL,
  Id_Device CHAR(38) NOT NULL,
  Code_Hash CHAR(64) NOT NULL,
  Expires_At DATETIME NOT NULL,
  Used_At DATETIME NULL,
  Revoked_At DATETIME NULL,
  Created_At DATETIME NOT NULL,
  Created_By CHAR(38) NULL,
  PRIMARY KEY (Id_Enrollment),
  UNIQUE KEY uq_device_enrollment_code_hash (Code_Hash),
  KEY idx_device_enrollment_active (Id_Device, Expires_At, Used_At, Revoked_At),
  CONSTRAINT fk_device_enrollment_device FOREIGN KEY (Id_Device) REFERENCES licensed_devices (Id_Device)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS device_challenges (
  Id_Challenge CHAR(38) NOT NULL,
  Id_Device CHAR(38) NOT NULL,
  Nonce_Value VARCHAR(128) NOT NULL,
  Nonce_Hash CHAR(64) NOT NULL,
  Expires_At DATETIME NOT NULL,
  Used_At DATETIME NULL,
  Credential_Issued_At DATETIME NULL,
  Created_At DATETIME NOT NULL,
  PRIMARY KEY (Id_Challenge),
  UNIQUE KEY uq_device_challenge_nonce_hash (Nonce_Hash),
  KEY idx_device_challenge_active (Id_Device, Expires_At, Used_At),
  CONSTRAINT fk_device_challenge_device FOREIGN KEY (Id_Device) REFERENCES licensed_devices (Id_Device)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS license_validation_events (
  Id_Event CHAR(38) NOT NULL,
  Id_Device CHAR(38) NULL,
  Id_Application CHAR(38) NULL,
  Id_Usuario CHAR(38) NULL,
  Id_License CHAR(38) NULL,
  Event_Type VARCHAR(60) NOT NULL,
  Result VARCHAR(30) NOT NULL,
  Reason VARCHAR(500) NULL,
  App_Version VARCHAR(80) NULL,
  Remote_IP VARCHAR(64) NULL,
  Created_At DATETIME NOT NULL,
  Created_By CHAR(38) NULL,
  PRIMARY KEY (Id_Event),
  KEY idx_license_validation_device_date (Id_Device, Created_At),
  KEY idx_license_validation_license_date (Id_License, Created_At),
  KEY idx_license_validation_type_date (Event_Type, Created_At),
  CONSTRAINT fk_license_validation_device FOREIGN KEY (Id_Device) REFERENCES licensed_devices (Id_Device),
  CONSTRAINT fk_license_validation_application FOREIGN KEY (Id_Application) REFERENCES licensed_applications (Id_Application),
  CONSTRAINT fk_license_validation_license FOREIGN KEY (Id_License) REFERENCES licenses (Id_License)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

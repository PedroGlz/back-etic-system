SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET CHARACTER SET utf8mb4;

USE license_system;

SET @max_devices_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_application_access'
    AND COLUMN_NAME = 'Max_Devices'
);
SET @max_devices_ddl = IF(
  @max_devices_exists = 0,
  'ALTER TABLE user_application_access ADD COLUMN Max_Devices INT NOT NULL DEFAULT 1 AFTER Valid_Until',
  'SELECT 1'
);
PREPARE licensing_statement FROM @max_devices_ddl;
EXECUTE licensing_statement;
DEALLOCATE PREPARE licensing_statement;

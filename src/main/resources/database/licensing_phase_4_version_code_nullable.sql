SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET CHARACTER SET utf8mb4;

USE license_system;

ALTER TABLE application_versions
  MODIFY COLUMN Version_Code BIGINT NULL;

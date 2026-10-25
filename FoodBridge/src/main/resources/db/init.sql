-- Run once as the MySQL root user. Tables are created automatically by Hibernate (ddl-auto=update).
CREATE DATABASE IF NOT EXISTS foodbridge CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'foodbridge'@'%' IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';
GRANT ALL PRIVILEGES ON foodbridge.* TO 'foodbridge'@'%';
FLUSH PRIVILEGES;

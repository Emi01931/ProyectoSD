-- Crear la base de datos
CREATE DATABASE IF NOT EXISTS criptomonedas_db;

-- Usar la base de datos
USE criptomonedas_db;

-- Tabla para Bitcoin
CREATE TABLE IF NOT EXISTS bitcoin (
    id INT AUTO_INCREMENT PRIMARY KEY,
    precio DECIMAL(11, 2) NOT NULL,
    fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Tabla para Ethereum
CREATE TABLE ethereum (
    id INT AUTO_INCREMENT PRIMARY KEY,
    precio DECIMAL(11, 2) NOT NULL,
    fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Tabla para Ripple
CREATE TABLE ripple (
    id INT AUTO_INCREMENT PRIMARY KEY,
    precio DECIMAL(11, 2) NOT NULL,
    fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Tabla para Solana
CREATE TABLE solana (
    id INT AUTO_INCREMENT PRIMARY KEY,
    precio DECIMAL(11, 2) NOT NULL,
    fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Tabla para Tron
CREATE TABLE tron (
    id INT AUTO_INCREMENT PRIMARY KEY,
    precio DECIMAL(11, 2) NOT NULL,
    fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Tabla para Dogecoin
CREATE TABLE dogecoin (
    id INT AUTO_INCREMENT PRIMARY KEY,
    precio DECIMAL(11, 2) NOT NULL,
    fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Tabla para Cardano
CREATE TABLE cardano (
    id INT AUTO_INCREMENT PRIMARY KEY,
    precio DECIMAL(11, 2) NOT NULL,
    fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Tabla para Hyperliquid
CREATE TABLE hyperliquid (
    id INT AUTO_INCREMENT PRIMARY KEY,
    precio DECIMAL(11, 2) NOT NULL,
    fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Tabla para Bitcoin Cash
CREATE TABLE bitcoin_cash (
    id INT AUTO_INCREMENT PRIMARY KEY,
    precio DECIMAL(11, 2) NOT NULL,
    fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Tabla para Chainlink
CREATE TABLE chainlink (
    id INT AUTO_INCREMENT PRIMARY KEY,
    precio DECIMAL(11, 2) NOT NULL,
    fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Inserts de ejemplo para cada tabla
/*
INSERT INTO bitcoin (precio) VALUES (50000.00);
INSERT INTO ethereum (precio) VALUES (3000.00);
INSERT INTO ripple (precio) VALUES (0.50);
INSERT INTO solana (precio) VALUES (100.00);
INSERT INTO tron (precio) VALUES (0.10);
INSERT INTO dogecoin (precio) VALUES (0.15);
INSERT INTO cardano (precio) VALUES (1.20);
INSERT INTO hyperliquid (precio) VALUES (5.00);
INSERT INTO bitcoin_cash (precio) VALUES (400.00);
INSERT INTO chainlink (precio) VALUES (20.00);

SELECT * FROM bitcoin ORDER BY fecha_registro DESC;
SELECT * FROM ethereum ORDER BY fecha_registro DESC;
SELECT * FROM ripple ORDER BY fecha_registro DESC;
SELECT * FROM solana ORDER BY fecha_registro DESC;
SELECT * FROM tron ORDER BY fecha_registro DESC;
SELECT * FROM dogecoin ORDER BY fecha_registro DESC;
SELECT * FROM cardano ORDER BY fecha_registro DESC;
SELECT * FROM hyperliquid ORDER BY fecha_registro DESC;
SELECT * FROM bitcoin_cash ORDER BY fecha_registro DESC;
SELECT * FROM chainlink ORDER BY fecha_registro DESC;
*/
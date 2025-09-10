-- File: database/setup_mysql.sql
-- This script creates the database structure for GPS tracking
-- Run this on both MySQL instances when using two machines

CREATE DATABASE IF NOT EXISTS gps_tracking;
USE gps_tracking;

-- Main table for storing GPS location data
CREATE TABLE IF NOT EXISTS location_data (
    id INT AUTO_INCREMENT PRIMARY KEY,
    latitude DECIMAL(10, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    timestamp DATETIME NOT NULL,
    receiver_id INT NOT NULL, -- Identifies which receiver captured this data
    
    -- This prevents duplicate entries from replication
    -- CHANGE: When using two machines, this prevents sync conflicts
    UNIQUE KEY unique_location (latitude, longitude, timestamp, receiver_id),
    
    -- Index for faster queries
    INDEX idx_timestamp (timestamp),
    INDEX idx_receiver (receiver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Create user for replication (for future two-machine setup)
-- CHANGE: On two machines, each MySQL needs this user for the OTHER machine
-- For now, we create it but won't use it
CREATE USER IF NOT EXISTS 'replication_user'@'%' IDENTIFIED BY 'repl_password_123';
GRANT REPLICATION SLAVE ON *.* TO 'replication_user'@'%';

-- Create application user
-- CHANGE: On two machines, adjust the host from 'localhost' to '%' or specific IPs
CREATE USER IF NOT EXISTS 'gps_app'@'__DB_HOST__' IDENTIFIED BY 'gps_password_123';
GRANT ALL PRIVILEGES ON gps_tracking.* TO 'gps_app'@'__DB_HOST__';

FLUSH PRIVILEGES;
-- =====================================================================
-- Corporate Context Management
-- Version 6.0.0 - Multi-Corporate Database Routing and User Management
-- =====================================================================
-- NO HARDCODING - All corporate configuration from database
-- Fully configurable and manageable from Admin Panel
-- =====================================================================

-- ============================================
-- CORPORATE DATABASE MAPPING
-- ============================================
CREATE TABLE IF NOT EXISTS ai_corporate_database_mapping (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    corporate_code VARCHAR(50) NOT NULL UNIQUE,
    database_key VARCHAR(50) NOT NULL,
    database_name VARCHAR(100),
    description TEXT,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_corporate_code (corporate_code),
    INDEX idx_database_key (database_key),
    INDEX idx_active (active)
);

-- ============================================
-- USER TYPE CONFIGURATION
-- ============================================
CREATE TABLE IF NOT EXISTS ai_user_type_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_type VARCHAR(50) NOT NULL UNIQUE,
    role_name VARCHAR(50) NOT NULL,
    can_access_multiple_corporates BOOLEAN DEFAULT FALSE,
    default_database_access VARCHAR(50), -- 'SINGLE', 'MULTI', 'ALL'
    description TEXT,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_type (user_type),
    INDEX idx_role_name (role_name),
    INDEX idx_active (active)
);

-- ============================================
-- USER-CORPORATE ASSIGNMENT
-- ============================================
CREATE TABLE IF NOT EXISTS ai_user_corporate_assignment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(100) NOT NULL,
    corporate_code VARCHAR(50) NOT NULL,
    user_type VARCHAR(50) NOT NULL,
    access_level VARCHAR(50) DEFAULT 'READ', -- 'READ', 'WRITE', 'FULL'
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    assigned_by VARCHAR(100),
    active BOOLEAN DEFAULT TRUE,
    INDEX idx_user_id (user_id),
    INDEX idx_corporate_code (corporate_code),
    INDEX idx_user_type (user_type),
    INDEX idx_active (active),
    UNIQUE KEY uk_user_corporate (user_id, corporate_code)
);

-- ============================================
-- DEFAULT USER TYPE CONFIGURATIONS
-- ============================================
INSERT INTO ai_user_type_config (user_type, role_name, can_access_multiple_corporates, default_database_access, description, active)
VALUES 
('CORPORATE_USER', 'CORPORATE_USER', FALSE, 'SINGLE', 'Users from specific corporates - access only their corporate database', TRUE),
('MONITORING_TEAM', 'MONITORING_TEAM', TRUE, 'ALL', 'Monitoring/operations team - read access to all corporate databases', TRUE),
('AMS_TEAM', 'AMS_TEAM', TRUE, 'MULTI', 'Application Management Services teams - access to assigned corporate databases', TRUE),
('ADMIN', 'ADMIN', TRUE, 'ALL', 'System administrators - full access to all databases', TRUE),
('GENERAL_USER', 'GENERAL_USER', FALSE, 'SINGLE', 'General users - corporate-specific or general access', TRUE);

-- =====================================================================
-- RAG Domain Documents Table
-- Version 4.0.0 - Domain Knowledge Base for RAG Engine
-- =====================================================================
-- NO HARDCODING - All domain knowledge from database
-- Fully configurable and manageable from Admin Panel
-- =====================================================================

-- ============================================
-- DOMAIN DOCUMENTS (For RAG Engine)
-- ============================================
CREATE TABLE IF NOT EXISTS ai_domain_documents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,                 -- Document content
    category VARCHAR(50) NOT NULL,          -- FAQ, POLICY, PRODUCT, SOP
    subcategory VARCHAR(100),              -- Optional subcategory
    tags JSON,                             -- Semantic tags for filtering
    embedding_vector JSON,                 -- Pre-computed embedding (384-dim)
    embedding_model VARCHAR(100),          -- Model used (e.g., "all-MiniLM-L6-v2")
    document_version INT DEFAULT 1,
    source_url VARCHAR(1000),              -- Optional source URL
    author VARCHAR(255),                   -- Optional author
    priority INT DEFAULT 0,                -- Retrieval priority
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category (category),
    INDEX idx_active_priority (active, priority DESC),
    INDEX idx_category_active (category, active)
);

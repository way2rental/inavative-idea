-- Generate Embeddings for All Scenarios
-- This should be run after application startup to populate embeddings
-- Or use the admin API endpoint: POST /api/admin/intelligence/embeddings/generate-all

-- Note: Embeddings must be generated via EmbeddingGenerationService (Java code)
-- which uses the AllMiniLmL6V2EmbeddingModel sentence transformer model.
-- This cannot be done via SQL.

-- To generate embeddings, use one of these methods:
-- 
-- Method 1: Via Admin API (Recommended)
-- POST http://localhost:8080/api/admin/intelligence/embeddings/generate-all
-- Headers: Authorization: Bearer <admin-token>
--
-- Method 2: Via Application Startup (Add to a @PostConstruct method)
-- Call: embeddingGenerationService.generateAllEmbeddings().block()
--
-- Method 3: Via Admin Panel
-- Navigate to: Admin > Intelligence > Embeddings > Generate All

-- After embeddings are generated, the EMBEDDING_SIMILARITY layer will automatically
-- use semantic similarity matching instead of string matching.

-- Verify embeddings were generated:
-- SELECT scenario_code, embedding_model, LENGTH(embedding_vector) as vector_length, active 
-- FROM ai_scenario_embeddings 
-- WHERE active = TRUE;

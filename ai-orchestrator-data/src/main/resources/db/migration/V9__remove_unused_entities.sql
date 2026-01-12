-- =====================================================================
-- Remove Unused Entities
-- Version 9.0.0 - Cleanup unused entities not used in business logic
-- =====================================================================
-- Removes entities that are not used in actual business logic:
-- - UserCorporateAssignment (not used anywhere)
-- - UserTypeConfig (not used anywhere)
-- - CorporateDatabaseMapping (feature deferred, not used in business logic)
-- =====================================================================

-- Drop tables (in reverse order of dependencies)
DROP TABLE IF EXISTS ai_user_corporate_assignment;
DROP TABLE IF EXISTS ai_user_type_config;
DROP TABLE IF EXISTS ai_corporate_database_mapping;

-- V7: Add Foreign Key Constraints for Data Integrity
-- Required for banking-grade data consistency
-- Note: MySQL doesn't support IF NOT EXISTS for constraints, 
-- so we use DROP IF EXISTS pattern

-- Drop existing constraints if they exist (idempotent)
SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

-- Foreign key: chat_messages.session_id -> chat_sessions.session_id
ALTER TABLE chat_messages
DROP FOREIGN KEY IF EXISTS fk_chat_messages_session;

ALTER TABLE chat_messages
ADD CONSTRAINT fk_chat_messages_session 
FOREIGN KEY (session_id) REFERENCES chat_sessions(session_id)
ON DELETE CASCADE;

-- Foreign key: role_scenario_map.scenario_code -> ai_scenarios.scenario_code
ALTER TABLE role_scenario_map
DROP FOREIGN KEY IF EXISTS fk_role_scenario_map_scenario;

ALTER TABLE role_scenario_map
ADD CONSTRAINT fk_role_scenario_map_scenario 
FOREIGN KEY (scenario_code) REFERENCES ai_scenarios(scenario_code)
ON DELETE CASCADE;

-- Foreign key: ai_response_mappings.scenario_code -> ai_scenarios.scenario_code
ALTER TABLE ai_response_mappings
DROP FOREIGN KEY IF EXISTS fk_response_mappings_scenario;

ALTER TABLE ai_response_mappings
ADD CONSTRAINT fk_response_mappings_scenario 
FOREIGN KEY (scenario_code) REFERENCES ai_scenarios(scenario_code)
ON DELETE CASCADE;

SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS;

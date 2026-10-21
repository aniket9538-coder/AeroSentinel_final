-- ============================================================
-- AeroSentinel — Feature 7 Phase 8 (F7-P8)
-- Authority Audit History, Lifecycle State Machine & Reassignment
-- ============================================================

-- 1. ENHANCE AUTHORITY ACTIONS TABLE FOR FULL AUDIT HISTORY
ALTER TABLE authority_actions
    ADD COLUMN IF NOT EXISTS previous_status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS new_status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS reason TEXT;

CREATE INDEX IF NOT EXISTS idx_authority_actions_alert_performed
    ON authority_actions(alert_id, performed_at DESC);

CREATE INDEX IF NOT EXISTS idx_authority_actions_type
    ON authority_actions(action_type);

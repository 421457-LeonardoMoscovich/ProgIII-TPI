-- Sprint 3 originally introduced engine persistence tables.
-- The current V1 schema already creates matches, match_events, and match_states,
-- so this migration stays idempotent for fresh databases and older branches.
CREATE INDEX IF NOT EXISTS idx_match_events_match_id ON match_events(match_id);

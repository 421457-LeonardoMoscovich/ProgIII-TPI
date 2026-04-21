-- Sprint 2 / BE-05: GIN indexes para búsqueda de cartas con pg_trgm
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_cards_name_trgm    ON cards USING gin(name gin_trgm_ops);
CREATE INDEX idx_cards_subtypes_gin ON cards USING gin(subtypes);

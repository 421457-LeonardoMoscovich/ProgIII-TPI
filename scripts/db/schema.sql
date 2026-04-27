-- Pokemon TCG final schema export.
-- Source of truth remains backend/src/main/resources/db/migration.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS cards (
    id              VARCHAR(32)  PRIMARY KEY,
    set_code        VARCHAR(16)  NOT NULL,
    number          VARCHAR(16)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    supertype       VARCHAR(32)  NOT NULL,
    subtypes        JSONB        NOT NULL DEFAULT '[]',
    hp              INTEGER,
    types           JSONB        NOT NULL DEFAULT '[]',
    retreat_cost    JSONB        NOT NULL DEFAULT '[]',
    weaknesses      JSONB,
    resistances     JSONB,
    attacks         JSONB,
    rules           JSONB,
    evolves_from    VARCHAR(128),
    image_small     VARCHAR(512),
    image_large     VARCHAR(512),
    raw_json        JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS decks (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    is_valid        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS deck_cards (
    deck_id         BIGINT       NOT NULL REFERENCES decks(id) ON DELETE CASCADE,
    card_id         VARCHAR(32)  NOT NULL REFERENCES cards(id),
    quantity        INTEGER      NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (deck_id, card_id)
);

CREATE TABLE IF NOT EXISTS matches (
    id              BIGSERIAL PRIMARY KEY,
    player1_id      BIGINT       NOT NULL REFERENCES users(id),
    player2_id      BIGINT       REFERENCES users(id),
    player1_deck_id BIGINT       NOT NULL REFERENCES decks(id),
    player2_deck_id BIGINT       REFERENCES decks(id),
    status          VARCHAR(16)  NOT NULL,
    result          VARCHAR(16),
    winner_id       BIGINT       REFERENCES users(id),
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS match_events (
    id              BIGSERIAL PRIMARY KEY,
    match_id        BIGINT       NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    sequence        INTEGER      NOT NULL,
    event_type      VARCHAR(32)  NOT NULL,
    actor_user_id   BIGINT       REFERENCES users(id),
    payload         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (match_id, sequence)
);

CREATE TABLE IF NOT EXISTS match_states (
    id              BIGSERIAL PRIMARY KEY,
    match_id        BIGINT       NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    turn_number     INTEGER      NOT NULL,
    state_snapshot  JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cards_set_code ON cards(set_code);
CREATE INDEX IF NOT EXISTS idx_cards_name ON cards(name);
CREATE INDEX IF NOT EXISTS idx_cards_supertype ON cards(supertype);
CREATE INDEX IF NOT EXISTS idx_cards_name_trgm ON cards USING gin(name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_cards_subtypes_gin ON cards USING gin(subtypes);
CREATE INDEX IF NOT EXISTS idx_decks_user_id ON decks(user_id);
CREATE INDEX IF NOT EXISTS idx_matches_status ON matches(status);
CREATE INDEX IF NOT EXISTS idx_matches_player1_id ON matches(player1_id);
CREATE INDEX IF NOT EXISTS idx_matches_player2_id ON matches(player2_id);
CREATE INDEX IF NOT EXISTS idx_match_events_match_created ON match_events(match_id, created_at);
CREATE INDEX IF NOT EXISTS idx_match_events_match_id ON match_events(match_id);
CREATE INDEX IF NOT EXISTS idx_match_states_match_id ON match_states(match_id, turn_number DESC);

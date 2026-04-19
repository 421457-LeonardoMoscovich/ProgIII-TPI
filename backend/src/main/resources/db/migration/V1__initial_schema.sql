-- Pokémon TCG — esquema inicial (Sprint 1 / BE-02)
-- Set XY1 (146 cartas). Snapshot de match en JSONB.

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Cartas del set xy1 cacheadas desde pokemontcg.io v2.
-- raw_json guarda el payload completo por si necesitamos campos extra.
CREATE TABLE cards (
    id              VARCHAR(32)  PRIMARY KEY,  -- ej: "xy1-1"
    set_code        VARCHAR(16)  NOT NULL,
    number          VARCHAR(16)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    supertype       VARCHAR(32)  NOT NULL,     -- Pokemon | Trainer | Energy
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

CREATE INDEX idx_cards_set_code ON cards(set_code);
CREATE INDEX idx_cards_name     ON cards(name);
CREATE INDEX idx_cards_supertype ON cards(supertype);

CREATE TABLE decks (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    is_valid        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_decks_user_id ON decks(user_id);

CREATE TABLE deck_cards (
    deck_id         BIGINT       NOT NULL REFERENCES decks(id) ON DELETE CASCADE,
    card_id         VARCHAR(32)  NOT NULL REFERENCES cards(id),
    quantity        INTEGER      NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (deck_id, card_id)
);

-- Estado agregado: WAITING / SETUP / ACTIVE / FINISHED
-- Resultado: PLAYER1_WIN / PLAYER2_WIN / DRAW / ABANDONED (nullable hasta terminar)
CREATE TABLE matches (
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

CREATE INDEX idx_matches_status     ON matches(status);
CREATE INDEX idx_matches_player1_id ON matches(player1_id);
CREATE INDEX idx_matches_player2_id ON matches(player2_id);

-- Event sourcing: cada acción de partida para replay/auditoría.
CREATE TABLE match_events (
    id              BIGSERIAL PRIMARY KEY,
    match_id        BIGINT       NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    sequence        INTEGER      NOT NULL,
    event_type      VARCHAR(32)  NOT NULL,
    actor_user_id   BIGINT       REFERENCES users(id),
    payload         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (match_id, sequence)
);

CREATE INDEX idx_match_events_match_created ON match_events(match_id, created_at);

-- Snapshot periódico del estado completo del engine para reconexión rápida.
CREATE TABLE match_states (
    id              BIGSERIAL PRIMARY KEY,
    match_id        BIGINT       NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    turn_number     INTEGER      NOT NULL,
    state_snapshot  JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_match_states_match_id ON match_states(match_id, turn_number DESC);

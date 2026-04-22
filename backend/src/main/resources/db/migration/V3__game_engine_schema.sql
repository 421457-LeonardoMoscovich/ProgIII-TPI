CREATE TABLE matches (
    id          VARCHAR(36)  PRIMARY KEY,
    player1_id  BIGINT       NOT NULL REFERENCES users(id),
    player2_id  BIGINT       NOT NULL REFERENCES users(id),
    phase       VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
    winner_id   BIGINT       REFERENCES users(id),
    started_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMP
);

CREATE TABLE match_events (
    id          BIGSERIAL    PRIMARY KEY,
    match_id    VARCHAR(36)  NOT NULL REFERENCES matches(id),
    event_type  VARCHAR(50)  NOT NULL,
    payload     JSONB        NOT NULL,
    occurred_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_match_events_match_id ON match_events(match_id);

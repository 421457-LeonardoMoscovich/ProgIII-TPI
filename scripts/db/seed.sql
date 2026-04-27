-- Demo users (passwords are BCrypt hashes of "pikachu123")
INSERT INTO users (username, email, password_hash)
VALUES
  ('ash',   'ash@pokemontcg.demo',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LkDDar7OODG'),
  ('misty', 'misty@pokemontcg.demo', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LkDDar7OODG')
ON CONFLICT (username) DO NOTHING;

-- Cards are seeded by the backend catalog bootstrap from pokemontcg.io set xy1.
-- Run the backend once before loading decks so cards exist in DB.

-- ============================================================
-- Deck: "Equipo Ash" — Charizard Fire/Water (60 cards)
-- ============================================================
INSERT INTO decks (name, user_id)
SELECT 'Equipo Ash', id FROM users WHERE username = 'ash'
ON CONFLICT DO NOTHING;

INSERT INTO deck_cards (deck_id, card_id, quantity)
SELECT d.id, c.card_id, c.quantity
FROM decks d
CROSS JOIN (VALUES
  ('xy1-8',  4),
  ('xy1-9',  3),
  ('xy1-10', 2),
  ('xy1-37', 4),
  ('xy1-38', 2),
  ('xy1-39', 1),
  ('xy1-54', 4),
  ('xy1-137', 4),
  ('xy1-138', 4),
  ('xy1-139', 4),
  ('xy1-140', 4),
  ('xy1-131', 12),
  ('xy1-132', 12)
) AS c(card_id, quantity)
WHERE d.name = 'Equipo Ash' AND d.user_id = (SELECT id FROM users WHERE username = 'ash')
ON CONFLICT (deck_id, card_id) DO UPDATE SET quantity = EXCLUDED.quantity;

-- ============================================================
-- Deck: "Equipo Misty" — Blastoise Water (60 cards)
-- ============================================================
INSERT INTO decks (name, user_id)
SELECT 'Equipo Misty', id FROM users WHERE username = 'misty'
ON CONFLICT DO NOTHING;

INSERT INTO deck_cards (deck_id, card_id, quantity)
SELECT d.id, c.card_id, c.quantity
FROM decks d
CROSS JOIN (VALUES
  ('xy1-37', 4),
  ('xy1-38', 3),
  ('xy1-39', 3),
  ('xy1-54', 4),
  ('xy1-8',  4),
  ('xy1-9',  2),
  ('xy1-137', 4),
  ('xy1-138', 4),
  ('xy1-139', 4),
  ('xy1-140', 4),
  ('xy1-132', 16),
  ('xy1-133', 8)
) AS c(card_id, quantity)
WHERE d.name = 'Equipo Misty' AND d.user_id = (SELECT id FROM users WHERE username = 'misty')
ON CONFLICT (deck_id, card_id) DO UPDATE SET quantity = EXCLUDED.quantity;

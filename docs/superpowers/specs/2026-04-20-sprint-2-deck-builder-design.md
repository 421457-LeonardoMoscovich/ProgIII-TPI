# Sprint 2 — Deck Builder: Design Spec

**Date:** 2026-04-20  
**Sprint:** 2 — Deck Builder  
**Status:** Approved

---

## Decisions

| Topic | Decision | Reason |
|-------|----------|--------|
| Auth | JWT stateless (Spring Security + jjwt) | WS auth in Sprint 5 needs JWT in handshake; stateless scales cleanly |
| Card search | name (partial) + supertype + subtype | Spec RNF-01 <500ms; covers all useful filter combos |
| Deck editor UX | Angular CDK DnD + click fallback | DnD pre-builds Sprint 5 pattern; click is accessible fallback |

---

## 1. Backend Architecture

### New packages

```
com.utn.pokemontcg
├── api/controller/
│   ├── AuthController          # POST /api/auth/register, /api/auth/login
│   └── DeckController          # CRUD /api/decks + POST /api/decks/{id}/validate
├── api/dto/
│   ├── AuthRequest             # username, password
│   ├── AuthResponse            # token (JWT string)
│   ├── DeckDto                 # id, name, userId, cards[], valid, errors[]
│   ├── DeckSummaryDto          # id, name, cardCount, valid (list view)
│   └── ValidationResultDto     # valid, errors[]
├── domain/model/
│   ├── User                    # JPA entity — id, username, passwordHash, createdAt
│   └── Deck / DeckCard         # JPA entities
├── domain/service/
│   ├── DeckValidator           # pure Java, zero Spring deps, ≥95% coverage
│   └── DeckService             # @Service, orchestrates repo + validator
├── infrastructure/persistence/
│   ├── UserRepository          # extends JpaRepository<User, Long>
│   └── DeckRepository          # extends JpaRepository<Deck, Long>
└── config/
    ├── SecurityConfig          # JWT filter chain, BCrypt bean
    └── JwtUtil                 # sign/verify JWT, read secret from properties
```

### Auth flow

```
POST /api/auth/register  { username, password }
  → BCrypt hash password
  → persist User
  → return AuthResponse { token }

POST /api/auth/login  { username, password }
  → load UserDetails by username
  → BCrypt matches
  → return AuthResponse { token }
```

- JWT secret: `app.jwt.secret` in `application.properties` (min 256-bit)
- JWT expiry: 24h (`app.jwt.expiration-ms=86400000`)
- `SecurityConfig` permits: `POST /api/auth/**`, `GET /api/cards/**`, `/swagger-ui/**`, `/v3/api-docs/**`
- All other endpoints require valid JWT

### DeckValidator — rules (pure Java, no Spring)

```java
public class DeckValidator {
    public ValidationResult validate(List<DeckCard> cards) { ... }
}
```

Rules enforced:
1. Exactly 60 cards total
2. Max 4 copies of any card by name — **exception:** supertype `Energy` + subtype `Basic` → unlimited
3. Max 1 card with subtype `AS TÁCTICO`
4. At least 1 Pokémon with subtype `Basic`

`ValidationResult`:
```java
record ValidationResult(boolean valid, List<String> errors) {}
```

All error messages in Spanish, actionable (name the card or count in the message).

### Card search

`subtypes` column is JSONB — requires GIN index + native query:

**Flyway V2:**
```sql
-- V2__add_search_indexes.sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;  -- required for gin_trgm_ops
CREATE INDEX idx_cards_supertype ON cards(supertype);
CREATE INDEX idx_cards_name_trgm ON cards USING gin(name gin_trgm_ops);
CREATE INDEX idx_cards_subtypes_gin ON cards USING gin(subtypes);
```

**CardRepository:**
```java
@Query(value = """
    SELECT * FROM cards
    WHERE (:name IS NULL OR name ILIKE '%' || :name || '%')
    AND (:supertype IS NULL OR supertype = :supertype)
    AND (:subtype IS NULL OR subtypes @> CAST(:subtype AS jsonb))
    AND set_code = 'xy1'
    ORDER BY number::int
    """, nativeQuery = true)
List<Card> search(
    @Param("name") String name,
    @Param("supertype") String supertype,
    @Param("subtype") String subtype  // pass as JSON array string e.g. "[\"Basic\"]"
);
```

`CardController` wraps the raw `subtype` query param into JSON array format before calling the repo:  
`subtype != null ? "[\"" + subtype + "\"]" : null`

Endpoint: `GET /api/cards/search?name=&supertype=&subtype=` — all params optional, all return xy1 cards if omitted.

### Deck CRUD endpoints

```
POST   /api/decks              → create deck (owner = JWT subject)
GET    /api/decks              → list caller's decks (DeckSummaryDto[])
GET    /api/decks/{id}         → full deck (DeckDto)
PUT    /api/decks/{id}         → update deck (owner only)
DELETE /api/decks/{id}         → delete deck (owner only)
POST   /api/decks/{id}/validate → validate without saving (ValidationResultDto)
```

Authorization: `DeckService` checks `deck.userId == currentUserId`, throws `403` otherwise.

`DeckController` extracts the current user via `SecurityContextHolder`:
```java
String username = SecurityContextHolder.getContext().getAuthentication().getName();
User user = userRepository.findByUsername(username).orElseThrow();
```
`JwtAuthFilter` populates the `SecurityContext` before the controller runs.

### Flyway migrations

| File | Purpose |
|------|---------|
| `V2__add_search_indexes.sql` | GIN indexes for card search |
| `V3__add_users_password.sql` | Add `password_hash` + `username` to `users` if not present in V1 |

Check V1 `users` table definition before writing V3 — may be a no-op if columns already exist.

---

## 2. Frontend Architecture

### New feature structure

```
src/app/
├── features/
│   ├── auth/
│   │   ├── login/login.component.ts       # form → AuthService.login()
│   │   └── register/register.component.ts # form → AuthService.register()
│   └── deck-builder/
│       ├── card-catalog/                  # search + filter panel
│       ├── deck-editor/                   # mazo actual + DnD drop zone
│       └── deck-list/                     # mis mazos
├── shared/components/
│   └── card-preview/                      # CardPreviewComponent (reusable Sprint 5)
└── core/
    ├── guards/auth.guard.ts               # redirect to /login if no JWT
    ├── interceptors/auth.interceptor.ts   # add Bearer header to all requests
    └── services/
        ├── auth.service.ts               # login/register, JWT in localStorage
        └── deck.service.ts               # CRUD decks, validate
```

### Routing

```
/login          → LoginComponent (public)
/register       → RegisterComponent (public)
/decks          → DeckListComponent (AuthGuard)
/decks/new      → DeckEditorComponent (AuthGuard)
/decks/:id      → DeckEditorComponent (AuthGuard)
```

### Deck Editor layout

Split panel:
- **Left:** CardCatalogComponent — search input + supertype dropdown + subtype dropdown + card grid (CardPreview tiles)
- **Right:** DeckEditorComponent — deck name input, `{count}/60` counter, validation badge (green ✓ / red ✗ + error list), card list

**DnD:** Angular CDK `DragDropModule`. Drag CardPreview from catalog → drop into deck list. Also "+" button on each card for click fallback.

**Live validation:** on deck change, debounce 300ms → `POST /api/decks/{id}/validate` (or local mirror of DeckValidator rules for instant feedback before save).

### CardPreview component

```typescript
@Input() card: Card;
@Input() showAddButton = false;   // deck builder mode
@Input() draggable = false;       // DnD mode
```

Displays: card image (URL from `card.images.small`), name, HP, supertype badge.  
Designed for zero changes when reused on match board in Sprint 5.

### JWT storage

`localStorage` key: `ptcg_token`. `AuthInterceptor` reads it and attaches to every HTTP request. `AuthGuard` checks presence + expiry (decode payload, check `exp`).

---

## 3. Testing

### Backend

**DeckValidator (JUnit 5, pure POJO — no Spring context):**
- ✅ 60 cards, all rules pass → `valid=true`
- ❌ 59 cards → "El mazo debe tener exactamente 60 cartas"
- ❌ 61 cards → same message
- ❌ 5 copies of non-Energy card → "Máximo 4 copias de [nombre]"
- ✅ 6+ Basic Energy cards → `valid=true`
- ❌ 0 Basic Pokémon → "El mazo debe tener al menos 1 Pokémon Básico"
- ❌ 2 AS TÁCTICO cards → "Solo se permite 1 carta AS TÁCTICO"
- ❌ multiple violations → all errors returned together
- Target: **≥95% line coverage** on `DeckValidator`

**DeckService (JUnit 5 + Mockito):**
- `createDeck` persists and returns DTO
- `updateDeck` with wrong owner → throws 403
- `validateDeck` delegates to DeckValidator, returns ValidationResultDto

**AuthController (@WebMvcTest + MockMvc):**
- Register with duplicate username → 409
- Login bad password → 401
- Successful login → 200 + JWT in body

**CardRepository search (@DataJpaTest, H2 or embedded PG):**
- Filter by name partial match
- Filter by supertype
- Filter by subtype (JSONB containment)
- All params null → returns all xy1 cards

### Frontend (Vitest + Angular Testing Library)

- `AuthGuard` redirects to `/login` when no JWT
- `AuthInterceptor` adds `Authorization: Bearer <token>` header
- `DeckEditorComponent` counter updates on add/remove
- `CardPreviewComponent` renders card name and image

---

## 4. Definition of Done

- [ ] `POST /api/auth/register` and `/login` return valid JWT
- [ ] `GET /api/cards/search` responds <500ms with all 3 filters working
- [ ] Full deck CRUD via REST, owner-enforced
- [ ] `DeckValidator` ≥95% coverage, all rule cases tested
- [ ] Deck editor: DnD + click, live validation, 0/60 counter
- [ ] `CardPreview` reusable (no coupling to deck-builder feature)
- [ ] `docs/reglas/validacion-mazos.md` written (DOC-02)
- [ ] CI green on develop

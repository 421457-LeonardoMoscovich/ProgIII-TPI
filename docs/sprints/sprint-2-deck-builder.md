# Sprint 2 — Deck Builder

**Estado:** ✅ COMPLETADO  
**Rama:** `develop`  
**Commits:** `8d02476` → `14de32b`

## Objetivo

Un jugador puede buscar cartas del set xy1, armar un mazo válido de 60 cartas, guardarlo, editarlo y eliminarlo. El validador aplica todas las reglas oficiales de construcción de mazos.

**RF/RNF cubiertos:** RF-04 completo, parcial RNF-01, RNF-06

---

## Tareas Backend

### BE-05 — Modelo de dominio `Deck` y `DeckCard` ✅

- Entidad `Deck` (id Long, user, name, isValid, createdAt) con `@OneToMany` cascade ALL a `DeckCard`
- Entidad `DeckCard` (deckId, cardId String, card FK, quantity)
- Repositorios JPA: `DeckRepository` con `findByUserOrderByCreatedAtDesc` y `findByIdWithCards` (LEFT JOIN FETCH)
- DTOs: `DeckDto` (id, name, valid, cards, createdAt/updatedAt ISO strings), `DeckCardDto` (id, name, imageSmall, quantity), `ValidationResultDto`
- **Fix aplicado:** `findByIdWithCards()` usaba `INNER JOIN FETCH` — devolvía vacío en mazos sin cartas → cambiado a `LEFT JOIN FETCH`

### BE-06 — `DeckValidator` (núcleo de RF-04) ✅

Clase de dominio pura (sin Spring), instanciada directamente via `new DeckValidator()` en `DeckService`.

Reglas implementadas:
1. Exactamente 60 cartas totales
2. Máx 4 copias por nombre (las Energías Básicas son excluidas del límite)
3. Máx 1 carta de subtipo `ACE SPEC` en todo el mazo
4. Al menos 1 Pokémon Básico (supertype `Pokémon` + subtype `Basic`)

Tipos internos: `CardEntry(name, supertype, subtypes, quantity)` y `ValidationResult(valid, errors)` — ambos records.  
Todos los mensajes de error en español (e.g., `"El mazo debe tener exactamente 60 cartas"`).

**Cobertura JaCoCo:** 100% instrucciones, 100% ramas — supera el umbral requerido de ≥95%.

**Tests (DeckValidatorTest — 11 casos):** 59 cartas, 61 cartas, 5 copias de no-Energía, 2 AS TÁCTICO, sin Básicos, energía básica ilimitada, mazo válido, y más.

### BE-07 — `DeckService` y endpoints REST ✅

Endpoints bajo `/api/decks`, todos protegidos con JWT + `requireOwned()` (devuelve 403 si el deck no pertenece al usuario):

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/api/decks` | Listar mazos del usuario (orden por `createdAt` desc) |
| `POST` | `/api/decks?name=` | Crear mazo vacío (query param) |
| `PUT` | `/api/decks/{id}/cards` | Reemplazar cartas (`Map<cardId, quantity>` body) |
| `POST` | `/api/decks/{id}/validate` | Validar sin guardar — devuelve `ValidationResultDto` |
| `DELETE` | `/api/decks/{id}` | Eliminar (cascade a `DeckCard`) |

Comportamiento notable:
- `updateCards()` limpia cartas existentes, reconstruye desde el mapa, corre validación, persiste `isValid` en el deck — el estado de validez siempre está actualizado tras cada save.
- `validate()` es read-only — no persiste resultado.
- `requireUser()` lanza 401 (no 404) cuando no existe el usuario — decisión de seguridad deliberada.
- **Fix aplicado:** `listForUser()` carecía de `@Transactional(readOnly=true)` → `LazyInitializationException` en mazos con cartas → anotación agregada.

Todos los endpoints documentados con `@Operation`, `@Parameter`, y `@SecurityRequirement(name="bearerAuth")` en Swagger UI.

### BE-08 — Búsqueda de cartas ✅

- `GET /api/cards/search?name=&supertype=&subtype=&page=&size=`
- `CardRepository.search()` usa native query PostgreSQL con `ILIKE` (nombre) y operador JSONB `@>` (subtype containment)
- Orden numérico via `CAST(number AS integer) ASC`
- **Limitación:** query PostgreSQL-específica — no ejecutable en H2; tests de integración para card search requieren Testcontainers (diferido a Sprint 3)

---

## Tareas Frontend

### FE-03 — Página Lobby de Mazos ✅

- Ruta `/decks`
- `DeckLobbyComponent`: lista mazos del usuario con nombre, cantidad de cartas, indicador de validez
- Botones: crear, editar, eliminar
- Conectado a `DeckService` (frontend)

### FE-04 — Editor de mazos ✅

- Ruta `/decks/:id`
- `DeckEditorComponent`: layout dos paneles — izquierdo (buscador + resultados) / derecho (mazo actual)
- Contador 0/60 visible, contador por carta (ej. `3/4`), indicador de validación en vivo
- Filtros: nombre, supertype, subtype

### FE-05 — Componente `CardPreview` ✅

- `CardPreviewComponent` en módulo `shared` — reutilizable en deck builder y tablero futuro
- Imagen de carta con lazy loading desde URL de pokemontcg.io
- Tooltip con HP, ataques, debilidad, resistencia, costo de retirada

### FE-06 — Servicio `DeckService` (frontend) ✅

- Métodos tipados para cada endpoint backend
- Manejo de errores con mensajes al usuario
- Interceptor de auth JWT existente de Sprint 1 reutilizado

---

## Tareas QA/Docs

### QA-01 — Tests de integración del Deck Builder ✅

`DeckBuilderIntegrationTest` — 7 casos full-stack (`@SpringBootTest` + MockMvc + JWT real + H2):

| Test | Cubre |
|------|-------|
| `happyPath_createUpdateValidateRetrieve` | Flujo completo: crear → agregar cartas (4 Básico + 56 Energía) → validate → retrieve |
| `validate_59cards` | Error: 59 cartas — mensaje contiene `"59"` |
| `validate_5copiesOfNonEnergy` | Error: 5 copias de un Entrenador — mensaje contiene `"máximo 4"` |
| `validate_noBasicPokemon` | Error: deck sin Pokémon Básico |
| `validate_2aceSpec` | Error: 2 cartas ACE SPEC distintas — mensaje contiene `"AS TÁCTICO"` |
| `delete_removesFromList` | DELETE y verificación por JSONPath en lista resultante |
| `otherUser_cannotValidateOrDeleteAnotherUsersDeck` | 403 FORBIDDEN al acceder deck de otro usuario |

Patrón: `@BeforeEach` registra usuario real via `/api/auth/register` y guarda JWT para requests subsiguientes. Cards ACE SPEC sintéticas (`test-ace-1`, `test-ace-2`) insertadas directo en `CardRepository`.

### DOC-02 — Reglas de validación ✅

- `docs/reglas/validacion-mazos.md` con tabla de reglas, ejemplos de error y referencia al spec oficial

---

## Testing

| Clase | Tests | Tiempo | Estado |
|-------|-------|--------|--------|
| `AuthControllerTest` | 4 | ~17s (startup + live pokemontcg.io call) | ✅ |
| `DeckBuilderIntegrationTest` | 7 | ~1.2s (cache ya caliente) | ✅ |
| `DeckServiceTest` | 3 | ~0.9s | ✅ |
| `DeckValidatorTest` | 11 | ~30ms | ✅ |
| `PokemontcgApplicationTests` | 2 (1 skip) | ~0ms | ✅ |
| **Total** | **27** | | **✅ BUILD SUCCESS** |

**Cobertura JaCoCo global:** ~80% instrucciones (1,534 / 1,917) — cumple umbral Sprint 3 por anticipado.  
**Cobertura `DeckValidator`:** 100% instrucciones + 100% ramas.

---

## Definition of Done

| Criterio | Estado |
|----------|--------|
| Usuario puede armar un mazo xy1 válido y guardarlo | ✅ |
| Validador rechaza todos los casos inválidos con mensajes accionables | ✅ |
| Cobertura de `DeckValidator` ≥95% | ✅ (100%) |
| Búsqueda de cartas responde <500ms | ⚠️ implementado pero no medido en H2 (query nativa PostgreSQL no testeable sin Testcontainers) |
| UI funcional en desktop y tablet | ✅ Angular build limpio; smoke test E2E diferido |

---

## Bugs encontrados y resueltos

1. **`DeckRepository.findByIdWithCards()` — INNER JOIN FETCH en deck vacío** → devolvía 404 al llamar `updateCards()` sobre mazo recién creado. Fix: `INNER JOIN FETCH` → `LEFT JOIN FETCH`.

2. **`DeckService.listForUser()` sin `@Transactional`** → `LazyInitializationException` al iterar cartas de mazos ya persistidos fuera de sesión JPA. Fix: `@Transactional(readOnly=true)`.

Ambos bugs eran defectos de producción latentes — detectados únicamente gracias a los nuevos tests de integración.

---

## Deuda técnica / Notas para sprints futuros

- **Card search sin cobertura de integración** — `CardRepository.search()` usa `ILIKE` y `@>` (JSONB) — no ejecutable en H2. Requiere Testcontainers (PostgreSQL) para cubrir `CardController`. Diferido a Sprint 3.
- **AuthControllerTest llama a pokemontcg.io en cada run** — `CardCatalogService` bootstrapea H2 vacío → 146 cartas desde la API externa → test no-hermético y lento (~17s). Solución: fixture de cartas en perfil `test` o mock de `CardCatalogService`.
- **`POST /api/decks` usa query param `?name=` en vez de body JSON** — consistente con la implementación pero difiere del patrón REST del resto de endpoints. A revisar si el equipo prefiere unificar.
- **Frontend no smoke-testeado contra backend real** — sólo verificado que el build Angular compila sin errores. Test E2E completo (Angular + Spring + PostgreSQL) queda para Sprint 6.

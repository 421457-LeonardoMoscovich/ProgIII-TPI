# Sprint 2 — Deck Builder

**Objetivo:** un jugador puede buscar cartas del set xy1, armar un mazo válido de 60 cartas, guardarlo, editarlo y eliminarlo. El validador aplica todas las reglas oficiales.

**Duración estimada:** 2 semanas
**RF/RNF cubiertos:** RF-04 completo, parcial RNF-01, RNF-06

## Tareas Backend

### BE-05 — Modelo de dominio `Deck` y `DeckCard`
- Entidad `Deck` (id, userId, name, createdAt, updatedAt)
- Entidad `DeckCard` (deckId, cardId, quantity)
- Repositorios JPA
- DTOs (`DeckDto`, `DeckSummaryDto`, `CreateDeckRequest`, `UpdateDeckRequest`)
- **Done:** CRUD básico por repositorio

### BE-06 — `DeckValidator` (núcleo de RF-04)
Servicio puro (sin dependencias de Spring) que valida:
- Exactamente 60 cartas totales
- Máx 4 copias por nombre (excepto Energía Básica)
- Máx 1 carta de subtipo `AS TÁCTICO` en todo el mazo
- Al menos 1 Pokémon Básico
- Devuelve `ValidationResult` con lista de errores descriptivos y accionables
- **Tests unitarios** con cobertura ≥95% (casos borde: 59/60/61, 5 copias, 2 AS TÁCTICO, sin Básicos, 4 Energía Especial vs Básica)
- **Done:** todos los casos cubiertos, mensajes en español accionables

### BE-07 — `DeckService` y endpoints REST
- `POST   /api/decks` — crear (valida antes de persistir)
- `GET    /api/decks` — listar del usuario
- `GET    /api/decks/{id}` — detalle con cartas
- `PUT    /api/decks/{id}` — actualizar
- `DELETE /api/decks/{id}` — eliminar
- `POST   /api/decks/{id}/validate` — valida sin guardar
- Manejo de errores con `@ControllerAdvice` → respuestas HTTP consistentes
- **Done:** todos los endpoints documentados en Swagger + tests de integración

### BE-08 — Búsqueda de cartas
- `GET /api/cards/search?set=xy1&name=&supertype=&subtype=&page=&size=`
- Paginación con Spring Data
- Filtros por nombre (like), supertype (Pokémon/Entrenador/Energía), subtype
- **Done:** responde <500ms con los 146 xy1 en memoria/BD (RNF-01)

## Tareas Frontend

### FE-03 — Página Lobby de Mazos
- Ruta `/decks`
- Lista de mazos del usuario con nombre, cantidad de cartas, validez
- Botones: crear mazo, editar, eliminar, duplicar
- **Done:** CRUD visual conectado al backend

### FE-04 — Editor de mazos
- Ruta `/decks/:id`
- Layout: panel izquierdo (buscador + resultados), panel derecho (mazo actual)
- Contador **0/60** siempre visible y prominente
- Contador por carta (ej. `3/4`)
- Indicador de validación en vivo (verde/rojo) con lista de errores
- Filtros: nombre, supertype, subtype
- **Done:** se puede armar un mazo válido de punta a punta sin recargar

### FE-05 — Componente `CardPreview`
- Muestra imagen de carta (del URL de pokemontcg.io) con lazy loading
- Tooltip con HP, ataques, debilidad, resistencia, costo de retirada
- Usa formato eficiente (webp si lo provee la API)
- Reutilizable en deck builder y tablero
- **Done:** componente aislado con tests unitarios (Jest/Karma)

### FE-06 — Servicio `DeckService` (frontend)
- Métodos para cada endpoint
- Tipado estricto de respuestas
- Manejo de errores con mensajes al usuario
- **Done:** integración end-to-end probada

## Tareas QA/Docs

### QA-01 — Tests de integración del Deck Builder
- Flujo: crear mazo → agregar cartas → validar → guardar → recuperar
- Casos negativos: 59 cartas, 5 copias, sin Básicos, 2 AS TÁCTICO
- **Done:** todos verdes en CI

### DOC-02 — Documentar reglas de validación
- `docs/reglas/validacion-mazos.md` con tabla de reglas y ejemplos
- **Done:** referencia accesible para el equipo

## Definition of Done del Sprint
- [ ] Usuario puede armar un mazo xy1 válido y guardarlo
- [ ] Validador rechaza todos los casos inválidos con mensajes accionables
- [ ] Cobertura de `DeckValidator` ≥95%
- [ ] Búsqueda de cartas responde <500ms
- [ ] UI funcional en desktop y tablet

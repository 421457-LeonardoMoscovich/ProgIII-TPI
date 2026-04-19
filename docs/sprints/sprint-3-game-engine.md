# Sprint 3 — Game Engine (núcleo del proyecto)

**Objetivo:** implementar el motor de reglas completo, aislado del transporte, con todos los componentes críticos y cobertura ≥90%. Al final se puede simular una partida completa desde código/tests.

**Duración estimada:** 3 semanas (el sprint más largo y crítico)
**RF/RNF cubiertos:** RF-01, RF-02 completos. RNF-02, RNF-03, RNF-04.

## Principio rector
El Game Engine vive en `com.utn.pokemontcg.domain.engine` y **no depende de Spring, JPA, WebSocket ni REST**. Se testea como código Java puro. Se integra más adelante vía Facade.

## Tareas

### ENG-01 — Modelo de dominio del juego
Entidades inmutables o con semántica clara:
- `GameState` (raíz agregada)
- `PlayerState` (hand, deck, discard, prizeCards, active, bench, stadium?, flags del turno)
- `PokemonInPlay` (card, attachedEnergies, attachedTool, damageCounters, statusConditions, turnsInPlay)
- `Card` (polimórfica: `PokemonCard`, `EnergyCard`, `TrainerCard` con subtipos)
- `TurnFlags` (energyAttachedThisTurn, retreatUsedThisTurn, supporterPlayedThisTurn, stadiumPlayedThisTurn, isFirstTurn)
- **Done:** modelo compilando con tests de construcción básicos

### ENG-02 — `TurnManager` (patrón State)
Máquina de estados de fase del turno:
- `DrawPhase` → `MainPhase` → `AttackPhase` → `BetweenTurnsPhase` → siguiente jugador
- Transiciones validadas (no se puede atacar desde Main sin anunciar ataque)
- Regla: primer jugador no roba en su primer turno
- Regla: no atacar en el primer turno del que empieza
- **Tests:** todas las transiciones válidas e inválidas
- **Done:** cobertura ≥90%

### ENG-03 — `RuleValidator` (crítico, ≥90% cobertura)
Valida cada acción antes de ejecutarla. Devuelve `ActionResult` (ok/rejected + motivo).
Acciones a validar:
- `playBasicToBench` (hay espacio, es Básico)
- `evolvePokemon` (no en turno de llegada, no en primer turno, evolución correcta)
- `attachEnergy` (1 por turno)
- `playTrainer` (Objeto ilimitado; Partidario 1/turno; Estadio 1/turno; Herramienta: máx 1 por Pokémon)
- `retreat` (1/turno, paga costo, sin Dormido/Paralizado)
- `useAbility`
- `declareAttack` (energía requerida, no primer turno del que empieza, no Dormido/Paralizado)
- **Tests:** matriz exhaustiva por acción × condición
- **Done:** ≥90% cobertura

### ENG-04 — `DamageCalculator` (crítico, ≥90% cobertura)
Implementa la secuencia de RF-01c paso 6:
1. Daño base del ataque
2. Modificadores sobre el atacante (Entrenadores, efectos)
3. Debilidad del defensor (×2)
4. Resistencia del defensor (−20, mínimo 0)
5. Modificadores sobre el defensor
6. Redondeo a contadores de daño (×10)
- API: `calculate(attacker, defender, attack, context) → DamageResult`
- Función pura, sin side effects
- **Tests:** casos combinatorios (debilidad + modificador, resistencia que lleva a 0, etc.)
- **Done:** ≥90% cobertura

### ENG-05 — `StatusEffectManager` (crítico, ≥90% cobertura)
Maneja las 5 condiciones especiales de RF-01e:
- **Dormido**: coin flip entre turnos, cara → despierta
- **Quemado**: coin flip, cruz → 2 contadores
- **Confundido**: al atacar, coin flip, cruz → falla + 3 contadores
- **Paralizado**: no ataca ni retira, se cura al final del turno en que fue paralizado
- **Envenenado**: 1 contador automático entre turnos

Reglas de incompatibilidad:
- Dormido / Confundido / Paralizado son mutuamente excluyentes (la nueva reemplaza)
- Quemado y Envenenado son independientes y coexisten con todo
- Orden fijo entre turnos: **(1) Envenenado → (2) Quemado → (3) Dormido → (4) Paralizado**
- Todas se eliminan al retirar a la Banca o evolucionar

**Tests:** todas las combinaciones, orden de procesamiento, limpieza al retirar/evolucionar
**Done:** ≥90% cobertura

### ENG-06 — `AttackResolver` (Chain of Responsibility)
Implementa la secuencia de 7 pasos de RF-01c como cadena de handlers:
1. `EnergyRequirementHandler`
2. `ConfusionCheckHandler`
3. `AttackSelectionHandler`
4. `AttackPrerequisitesHandler` (coin flips previos)
5. `AttackModifiersHandler` (cancelación/modificación)
6. `DamageCalculationHandler` (delega en `DamageCalculator`)
7. `PostDamageEffectsHandler` (condiciones, descartes, daño a banca, curación)
- Cada handler es testeable en aislamiento
- **Done:** ejecuta ataques simples y complejos correctamente

### ENG-07 — `VictoryConditionChecker`
- Victoria por premios (última carta tomada)
- Victoria por knockout total (rival sin Pokémon)
- Derrota por mazo vacío al intentar robar
- Detección de Muerte Súbita (ambas simultáneas)
- Se invoca después de cada acción que pueda terminar la partida
- **Done:** cobertura ≥90%, Muerte Súbita probada

### ENG-08 — Setup de partida (RF-01a)
- Barajar mazos (con semilla inyectable para tests deterministas)
- Robo inicial de 7 cartas
- **Mulligan**: detección, show al rival (evento), re-barajar, re-robar, 1 carta extra al rival por mulligan
- Colocación inicial de Activo y Banca (hasta 5)
- Toma de 6 cartas de Premio
- Coin flip para quién empieza
- **Tests:** mulligan simple, mulligan múltiple, mulligan del segundo jugador
- **Done:** cobertura ≥90%

### ENG-09 — Proceso de Knockout (RF-01d)
- Detección cuando contadores × 10 ≥ HP
- Descarte del Pokémon + cartas unidas
- 1 carta de Premio (2 si es Pokémon-EX)
- Reemplazo obligatorio desde Banca
- Si no hay Pokémon en Banca → derrota
- **Done:** cobertura ≥90%

### ENG-10 — `GameEngineFacade`
API pública única del motor:
```java
GameEngineFacade {
  GameState startMatch(Deck p1, Deck p2, long seed);
  ActionResult applyAction(GameState state, PlayerAction action);
  GameState processBetweenTurns(GameState state);
  GameEvent[] getEventsSinceLastApply();
}
```
- Oculta toda la complejidad interna
- No expone estructuras mutables
- **Done:** API limpia, documentada con Javadoc

### ENG-11 — Sistema de eventos (base de Observer)
- `GameEvent` (sealed): `TurnStarted`, `CardDrawn`, `PokemonPlayed`, `EnergyAttached`, `AttackResolved`, `DamageDealt`, `KnockoutOccurred`, `PrizeTaken`, `StatusApplied`, `MatchEnded`, ...
- Lista inmutable emitida por cada acción
- Base para el log persistente (Sprint 4) y las notificaciones WS (Sprint 5)
- **Done:** todos los eventos definidos con campos tipados

### ENG-12 — Catálogo inicial de ataques y cartas de Entrenador (Strategy)
- Interfaz `AttackEffect` y `TrainerEffect`
- Implementar todos los ataques y trainers del set xy1 que tengan efectos especiales
- Registro central (`EffectRegistry`) que mapea `cardId + effectName → Strategy`
- Para ataques sin efecto especial, Strategy por defecto (solo daño)
- **Done:** cobertura suficiente de cartas xy1 para simular partidas representativas

## Tests de integración del motor

### ENG-TEST-01 — Partida completa simulada
Test que corre una partida de principio a fin usando solo el Facade, con decks deterministas:
- Setup con coin flip fijo
- Robo inicial
- 10+ turnos
- Knockouts
- Victoria por premios
**Done:** test pasa consistentemente

### ENG-TEST-02 — Escenarios específicos
- Mulligan múltiple (3+ seguidos)
- Evolución Fase 1 → Fase 2
- Knockout de Pokémon-EX (2 premios)
- Pokémon con 3 condiciones simultáneas (Quemado + Envenenado + Paralizado)
- Muerte Súbita
- Derrota por mazo vacío

## Definition of Done del Sprint
- [ ] `RuleValidator`, `DamageCalculator`, `StatusEffectManager` con cobertura ≥90%
- [ ] Engine sin dependencias a Spring/JPA/WebSocket
- [ ] Partida completa simulable desde tests
- [ ] Todos los patrones (State, Strategy, CoR) implementados
- [ ] Eventos emitidos para toda acción relevante

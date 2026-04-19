# Sprint 5 — Tiempo real y UI del tablero

**Objetivo:** sincronización bidireccional vía WebSockets, tablero interactivo con drag & drop, panel de acciones contextual, log y notificaciones. Al final del sprint, dos jugadores pueden jugar una partida completa desde el navegador.

**Duración estimada:** 3 semanas
**RF/RNF cubiertos:** RF-06, RF-07 completos. RNF-01, RNF-05, RNF-06.

## Tareas Backend

### WS-01 — Configuración de WebSocket/STOMP
- Config con `@EnableWebSocketMessageBroker`
- Broker simple in-memory, topics `/topic/match/{id}`
- Endpoint STOMP `/ws` con SockJS fallback
- CORS configurado para desarrollo
- **Done:** cliente puede conectar y suscribirse

### WS-02 — Autenticación y autorización de sesión WS
- Validar que quien se suscribe a `/topic/match/{id}` es participante
- Interceptor que valida token/sesión en el CONNECT
- **Done:** intento de sub con usuario ajeno es rechazado

### WS-03 — Integrar Observer del engine con WS
- `GameEventPublisher` suscribe al flujo de eventos de la partida
- Por cada evento, publica a ambos jugadores un DTO filtrado según su vista
- Un evento puede producir dos mensajes diferentes (uno para cada jugador) si contiene info sensible
- **Done:** ambos clientes reciben actualizaciones en vivo

### WS-04 — Mensajería de acciones
- Cliente envía acción por STOMP a `/app/match/{id}/action`
- Backend valida, aplica, persiste, publica eventos
- Respuesta al emisor (ACK/NACK) por `/user/queue/ack`
- **Done:** latencia de acción <200ms (RNF-01)

### WS-05 — Reconexión robusta
- Cliente incluye `lastEventSequence` al reconectar
- Backend envía snapshot actual + eventos faltantes desde esa secuencia
- Heartbeat STOMP configurado
- **Done:** matar el frontend y reabrir → estado restaurado, sin acciones perdidas

## Tareas Frontend

### FE-09 — Cliente WebSocket/STOMP
- Servicio `MatchSocketService` con `@stomp/stompjs` + SockJS
- Reconexión automática con backoff
- Observable de eventos tipados
- Método `send(action)` y `connect(matchId, token)`
- **Done:** integración clean con RxJS

### FE-10 — `MatchStateStore` (estado reactivo)
- Signal/BehaviorSubject con el estado actual
- Reducer aplica eventos entrantes al estado local (solo para UI; el backend es la verdad)
- **Done:** la UI siempre refleja el último estado recibido

### FE-11 — Layout del tablero (RF-07)
- Ruta `/match/:id`
- Zonas:
  - **Oponente (arriba)**: Activo, Banca (5 slots), mazo (contador), premios (6 slots), descarte (pila), cantidad de cartas en mano
  - **Zona compartida (centro)**: Estadio activo
  - **Jugador (abajo)**: Activo, Banca, mazo, premios, descarte
  - **Mano del jugador**: franja horizontal inferior con drag handle
- Responsivo: desktop y tablet
- **Done:** layout visual terminado con placeholders

### FE-12 — Drag & Drop (Angular CDK)
Implementar drag & drop para:
- Pokémon Básico de mano → Banca
- Energía de mano → Pokémon (Activo o Banca)
- Herramienta de mano → Pokémon
- Carta de Entrenador de mano → zona/target requerido
- Evolución: Pokémon de mano sobre Pokémon en juego (con validación visual previa)
- Cada drop dispara una acción vía WS
- Feedback visual: highlights de targets válidos, drop zones activas
- **Done:** todas las interacciones funcionan

### FE-13 — Panel de acciones contextual
- Botones: Evolucionar, Unir Energía, Jugar Entrenador, Retirar, Atacar, Finalizar Turno
- Habilitados/deshabilitados según fase del turno y flags (ej. si ya unió energía, botón deshabilitado)
- Tooltips explicando por qué un botón está deshabilitado
- **Done:** coherente con las reglas

### FE-14 — Visualización de Pokémon en juego
Componente `PokemonInPlay`:
- HP actual / HP máximo (barra visual)
- Contadores de daño (fichas)
- Energías unidas (pequeños íconos por tipo)
- Herramienta equipada (badge)
- Condición especial:
  - **Dormido/Confundido/Paralizado**: rotación visual de la carta (90° izq, 180°, 90° der)
  - **Quemado/Envenenado**: marcador/overlay
- **Done:** todos los estados representados visualmente

### FE-15 — Modal de selección de ataque
- Al clickear "Atacar", muestra ataques disponibles
- Cada ataque: nombre, costo (íconos de energía), daño, texto
- Deshabilitados los que no tienen energía suficiente (con mensaje)
- Confirmación antes de ejecutar (los ataques terminan el turno)
- **Done:** UX clara, sin ambigüedad

### FE-16 — Log de acciones
- Panel lateral (colapsable) con historial cronológico
- Cada evento con icono, jugador, descripción, turno
- Auto-scroll al último evento
- Filtros por tipo (opcional)
- **Done:** log completo visible durante la partida

### FE-17 — Notificaciones visuales
- Toast / banner para eventos importantes:
  - Inicio de turno
  - Ataque resuelto (daño infligido)
  - Knockout
  - Carta de Premio tomada
  - Condición especial aplicada
  - Fin de partida (modal con resultado)
- **Done:** todos los eventos clave notificados

### FE-18 — Habilidades de Pokémon (Abilities)
- Botón visible en Pokémon que tenga habilidad disponible
- Modal con descripción y confirmación
- Envía acción `useAbility`
- **Done:** flujo completo

### FE-19 — Pantalla de Setup (mulligan)
- Muestra mano inicial con visualización de si hay Básico
- Si no hay: modal "Mulligan — se muestra tu mano al rival", con botón "Continuar"
- Si el rival hizo mulligan, notificación + opción de robar carta extra
- Colocación manual de Activo y Banca iniciales
- **Done:** setup completo jugable

## Tareas QA

### QA-03 — Test E2E de partida completa (Cypress o Playwright)
- Dos navegadores en paralelo (dos sesiones)
- Crear mazo → lobby → crear partida → otro se une → setup → 2–3 turnos → verificar sincronización
- **Done:** verde en CI

## Definition of Done del Sprint
- [ ] Dos jugadores pueden jugar una partida completa en vivo
- [ ] Reconexión sin pérdida de estado ni acciones
- [ ] Drag & drop funcional para todas las interacciones
- [ ] Panel de acciones contextual y coherente
- [ ] Log de acciones y notificaciones visibles
- [ ] Responsive en desktop y tablet
- [ ] Mano del rival nunca visible (verificado)

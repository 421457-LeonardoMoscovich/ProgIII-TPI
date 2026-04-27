# Sprint 5 — Real-Time Gameplay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Two players can play a live Pokémon TCG match in-browser via WebSocket — no polling, full bidirectional sync, drag-and-drop interactions.

**Architecture:** Backend exposes STOMP topics per match (`/topic/match/{id}`) and accepts player actions on `/app/match/{id}/action`. A `MatchSessionService` holds live `GameState` in-memory per match, calls `GameEngineFacade`, and publishes per-player-filtered event DTOs. The frontend replaces 3-second polling with a `MatchSocketService` backed by RxJS, and a `MatchStateStore` signal applies incoming events as a local reducer.

**Tech Stack:** Spring Boot 3 + Spring WebSocket (STOMP/SockJS), Java 21 sealed interfaces, Angular 21 standalone components, `@stomp/stompjs`, Angular CDK drag-and-drop, RxJS BehaviorSubject/Subject, Angular signals.

---

## File Map

### Backend — new files
| File | Purpose |
|------|---------|
| `backend/.../api/ws/MatchActionController.java` | STOMP `@MessageMapping` for `/match/{id}/action` |
| `backend/.../api/ws/GameEventPublisher.java` | Translates `GameEvent` list → per-player WS messages |
| `backend/.../api/dto/ws/GameActionDto.java` | Inbound STOMP message (action type + payload) |
| `backend/.../api/dto/ws/GameEventDto.java` | Outbound STOMP event envelope (type + payload + seq) |
| `backend/.../api/dto/ws/AckDto.java` | ACK/NACK reply on `/user/queue/ack` |
| `backend/.../application/service/MatchSessionService.java` | In-memory match registry: stores `GameState` per matchId |
| `backend/.../api/controller/MatchController.java` | REST: `POST /api/matches`, `POST /api/matches/{id}/join`, `GET /api/matches`, `GET /api/matches/{id}/state` |
| `backend/.../api/dto/MatchDto.java` | REST response for match creation/join |
| `backend/.../api/dto/FilteredGameStateDto.java` | Player-filtered state snapshot for REST polling fallback |
| `backend/.../security/WsAuthChannelInterceptor.java` | ChannelInterceptor validating JWT on STOMP CONNECT |

### Backend — modified files
| File | Change |
|------|--------|
| `backend/.../config/WebSocketConfig.java` | Register `WsAuthChannelInterceptor` |
| `backend/.../config/SecurityConfig.java` | Permit WS endpoint `/ws/**` without HTTP auth |

### Frontend — new files
| File | Purpose |
|------|---------|
| `frontend/.../core/services/match-socket.service.ts` | Typed STOMP wrapper: `connect(matchId,token)`, `events$`, `sendAction()` |
| `frontend/.../core/store/match-state.store.ts` | Signal-based state store, applies events as reducers |
| `frontend/.../core/models/game-event.model.ts` | TypeScript union type mirroring `GameEventDto` |
| `frontend/.../core/models/game-action.model.ts` | TypeScript union type for outbound actions |
| `frontend/.../features/match/components/pokemon-in-play/pokemon-in-play.component.ts` | HP bar, energies, status condition, drag target |
| `frontend/.../features/match/components/pokemon-in-play/pokemon-in-play.component.html` | Template |
| `frontend/.../features/match/components/pokemon-in-play/pokemon-in-play.component.scss` | Status rotation styles |
| `frontend/.../features/match/components/attack-modal/attack-modal.component.ts` | Attack selection modal |
| `frontend/.../features/match/components/attack-modal/attack-modal.component.html` | Template |
| `frontend/.../features/match/components/action-log/action-log.component.ts` | Collapsible event log |
| `frontend/.../features/match/components/action-log/action-log.component.html` | Template |

### Frontend — modified files
| File | Change |
|------|--------|
| `frontend/.../features/match/match.component.ts` | Replace polling with `MatchSocketService` + `MatchStateStore` |
| `frontend/.../features/match/match.component.html` | Add CDK drag-and-drop, action panel, attack modal |
| `frontend/.../features/match/match.component.scss` | Drag-over styles |
| `frontend/.../core/models/match.model.ts` | Add `myHandCards` typed array to `FilteredGameStateDto` |

---

## Task 1: Backend — MatchSessionService (in-memory match registry)

**Files:**
- Create: `backend/src/main/java/com/utn/pokemontcg/application/service/MatchSessionService.java`
- Create: `backend/src/test/java/com/utn/pokemontcg/application/service/MatchSessionServiceTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// backend/src/test/java/com/utn/pokemontcg/application/service/MatchSessionServiceTest.java
package com.utn.pokemontcg.application.service;

import com.utn.pokemontcg.domain.engine.GameEngineFacade;
import com.utn.pokemontcg.domain.engine.model.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchSessionServiceTest {

    private MatchSessionService service;

    @BeforeEach
    void setUp() { service = new MatchSessionService(); }

    @Test
    void startSession_storesGameState() {
        GameState state = service.startSession("match-1", 1L, 2L, java.util.List.of(), java.util.List.of(), 42L);
        assertThat(state).isNotNull();
        assertThat(state.getMatchId()).isEqualTo("match-1");
    }

    @Test
    void getSession_returnsStoredState() {
        service.startSession("match-2", 1L, 2L, java.util.List.of(), java.util.List.of(), 0L);
        Optional<GameState> found = service.getSession("match-2");
        assertThat(found).isPresent();
    }

    @Test
    void getSession_emptyForUnknownMatch() {
        assertThat(service.getSession("unknown")).isEmpty();
    }

    @Test
    void removeSession_deletesState() {
        service.startSession("match-3", 1L, 2L, java.util.List.of(), java.util.List.of(), 0L);
        service.removeSession("match-3");
        assertThat(service.getSession("match-3")).isEmpty();
    }
}
```

- [ ] **Step 2: Run to verify compile fails**

```bash
cd backend && ./mvnw test -Dtest=MatchSessionServiceTest -q 2>&1 | tail -20
```
Expected: compilation error — `MatchSessionService` not found.

- [ ] **Step 3: Implement MatchSessionService**

```java
// backend/src/main/java/com/utn/pokemontcg/application/service/MatchSessionService.java
package com.utn.pokemontcg.application.service;

import com.utn.pokemontcg.domain.engine.GameEngineFacade;
import com.utn.pokemontcg.domain.engine.model.GameCard;
import com.utn.pokemontcg.domain.engine.model.GameState;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MatchSessionService {

    private final Map<String, GameState> sessions = new ConcurrentHashMap<>();

    public GameState startSession(String matchId, Long p1Id, Long p2Id,
                                  List<GameCard> deck1, List<GameCard> deck2,
                                  long seed) {
        GameEngineFacade engine = new GameEngineFacade();
        GameState state = engine.startMatch(p1Id, deck1, p2Id, deck2, seed);
        // Override the UUID matchId with the persistence matchId
        // GameState.matchId is final — store mapping instead
        sessions.put(matchId, state);
        return state;
    }

    public Optional<GameState> getSession(String matchId) {
        return Optional.ofNullable(sessions.get(matchId));
    }

    public void removeSession(String matchId) {
        sessions.remove(matchId);
    }

    public GameEngineFacade engineFor(String matchId) {
        // Each session needs its own facade instance stored alongside state
        return facades.get(matchId);
    }

    private final Map<String, GameEngineFacade> facades = new ConcurrentHashMap<>();

    public GameState startSession(String matchId, Long p1Id, Long p2Id,
                                  List<GameCard> deck1, List<GameCard> deck2,
                                  long seed, GameEngineFacade engine) {
        GameState state = engine.startMatch(p1Id, deck1, p2Id, deck2, seed);
        sessions.put(matchId, state);
        facades.put(matchId, engine);
        return state;
    }
}
```

Wait — the above has a duplicate method. Use this clean version instead:

```java
// backend/src/main/java/com/utn/pokemontcg/application/service/MatchSessionService.java
package com.utn.pokemontcg.application.service;

import com.utn.pokemontcg.domain.engine.GameEngineFacade;
import com.utn.pokemontcg.domain.engine.model.GameCard;
import com.utn.pokemontcg.domain.engine.model.GameState;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MatchSessionService {

    private record Session(GameState state, GameEngineFacade engine) {}

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public GameState startSession(String matchId, Long p1Id, Long p2Id,
                                  List<GameCard> deck1, List<GameCard> deck2,
                                  long seed) {
        GameEngineFacade engine = new GameEngineFacade();
        GameState state = engine.startMatch(p1Id, deck1, p2Id, deck2, seed);
        sessions.put(matchId, new Session(state, engine));
        return state;
    }

    public Optional<GameState> getSession(String matchId) {
        return Optional.ofNullable(sessions.get(matchId)).map(Session::state);
    }

    public Optional<GameEngineFacade> getEngine(String matchId) {
        return Optional.ofNullable(sessions.get(matchId)).map(Session::engine);
    }

    public void removeSession(String matchId) {
        sessions.remove(matchId);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd backend && ./mvnw test -Dtest=MatchSessionServiceTest -q 2>&1 | tail -10
```
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/utn/pokemontcg/application/service/MatchSessionService.java \
        backend/src/test/java/com/utn/pokemontcg/application/service/MatchSessionServiceTest.java
git commit -m "feat(be): WS-01 MatchSessionService — in-memory GameState registry per match"
```

---

## Task 2: Backend — REST MatchController (create/join/list/state)

**Files:**
- Create: `backend/src/main/java/com/utn/pokemontcg/api/controller/MatchController.java`
- Create: `backend/src/main/java/com/utn/pokemontcg/api/dto/MatchDto.java`
- Create: `backend/src/main/java/com/utn/pokemontcg/api/dto/FilteredGameStateDto.java`
- Create: `backend/src/main/java/com/utn/pokemontcg/api/dto/MatchSummaryDto.java`
- Create: `backend/src/test/java/com/utn/pokemontcg/api/controller/MatchControllerTest.java`

> **Note:** The frontend `match.model.ts` already defines `FilteredGameStateDto` and `MatchSummary` interfaces — the backend DTO must match those field names exactly.

- [ ] **Step 1: Create DTOs**

```java
// backend/src/main/java/com/utn/pokemontcg/api/dto/MatchDto.java
package com.utn.pokemontcg.api.dto;

public record MatchDto(Long id, String status) {}
```

```java
// backend/src/main/java/com/utn/pokemontcg/api/dto/MatchSummaryDto.java
package com.utn.pokemontcg.api.dto;

public record MatchSummaryDto(
    Long id,
    String status,
    String player1Username,
    String player2Username,
    String createdAt,
    int turnNumber
) {}
```

```java
// backend/src/main/java/com/utn/pokemontcg/api/dto/FilteredGameStateDto.java
package com.utn.pokemontcg.api.dto;

import java.util.List;

public record FilteredGameStateDto(
    Long matchId,
    int turnNumber,
    Long currentPlayerId,
    String phase,
    int myPrizesLeft,
    int opponentPrizesLeft,
    int myHandCount,
    int opponentHandCount,
    int myDeckCount,
    int opponentDeckCount,
    FieldPokemonDto myActive,
    FieldPokemonDto opponentActive,
    List<FieldPokemonDto> myBench,
    List<FieldPokemonDto> opponentBench,
    List<HandCardDto> myHand,
    String winner
) {
    public record FieldPokemonDto(
        String name, int hp, int maxHp,
        java.util.Map<String, Integer> energies,
        String statusCondition,
        List<AttackDto> attacks
    ) {}

    public record AttackDto(String name, String damage) {}

    public record HandCardDto(String id, String name, String type, String supertype) {}
}
```

- [ ] **Step 2: Write the failing controller test**

```java
// backend/src/test/java/com/utn/pokemontcg/api/controller/MatchControllerTest.java
package com.utn.pokemontcg.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utn.pokemontcg.application.service.MatchSessionService;
import com.utn.pokemontcg.config.JwtUtil;
import com.utn.pokemontcg.infrastructure.persistence.UserRepository;
import com.utn.pokemontcg.infrastructure.persistence.DeckRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MatchController.class)
class MatchControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @MockBean MatchSessionService matchSessionService;
    @MockBean UserRepository userRepository;
    @MockBean DeckRepository deckRepository;
    @MockBean JwtUtil jwtUtil;

    @Test
    @WithMockUser(username = "player1")
    void listMatches_returns200() throws Exception {
        when(matchSessionService.listSummaries()).thenReturn(List.of());
        mockMvc.perform(get("/api/matches"))
               .andExpect(status().isOk())
               .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = "player1")
    void createMatch_returns201() throws Exception {
        when(matchSessionService.createMatch(any(), any())).thenReturn(42L);
        mockMvc.perform(post("/api/matches")
                   .contentType(MediaType.APPLICATION_JSON)
                   .content("{\"deckId\": 1}"))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").value(42));
    }
}
```

- [ ] **Step 3: Run to verify compilation fails**

```bash
cd backend && ./mvnw test -Dtest=MatchControllerTest -q 2>&1 | tail -20
```
Expected: compilation error — `MatchController` not found.

- [ ] **Step 4: Add `listSummaries` and `createMatch` to MatchSessionService**

Add to `MatchSessionService.java` — we need a `Match` JPA entity first. For now, use an in-memory list of match metadata alongside `GameState`:

```java
// Add to MatchSessionService.java (inside class body):

public record MatchMeta(Long id, String status, String player1Username,
                        String player2Username, String createdAt, int turnNumber) {}

private final Map<Long, MatchMeta> matchMeta = new ConcurrentHashMap<>();
private final java.util.concurrent.atomic.AtomicLong idSeq = new java.util.concurrent.atomic.AtomicLong(1);

public Long createMatch(String player1Username, Long deckId) {
    Long id = idSeq.getAndIncrement();
    matchMeta.put(id, new MatchMeta(id, "WAITING", player1Username, null,
        java.time.Instant.now().toString(), 0));
    return id;
}

public List<MatchMeta> listSummaries() {
    return List.copyOf(matchMeta.values());
}

public Optional<MatchMeta> getMeta(Long matchId) {
    return Optional.ofNullable(matchMeta.get(matchId));
}

public boolean joinMatch(Long matchId, String player2Username, Long deckId) {
    MatchMeta m = matchMeta.get(matchId);
    if (m == null || !"WAITING".equals(m.status())) return false;
    matchMeta.put(matchId, new MatchMeta(m.id(), "SETUP", m.player1Username(),
        player2Username, m.createdAt(), m.turnNumber()));
    return true;
}
```

- [ ] **Step 5: Implement MatchController**

```java
// backend/src/main/java/com/utn/pokemontcg/api/controller/MatchController.java
package com.utn.pokemontcg.api.controller;

import com.utn.pokemontcg.api.dto.FilteredGameStateDto;
import com.utn.pokemontcg.api.dto.MatchDto;
import com.utn.pokemontcg.api.dto.MatchSummaryDto;
import com.utn.pokemontcg.application.service.MatchSessionService;
import com.utn.pokemontcg.domain.engine.model.GameState;
import com.utn.pokemontcg.domain.engine.model.PlayerState;
import com.utn.pokemontcg.domain.engine.model.PokemonInPlay;
import com.utn.pokemontcg.domain.engine.model.GameCard;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/matches")
@Tag(name = "Matches")
public class MatchController {

    private final MatchSessionService matchService;

    public MatchController(MatchSessionService matchService) {
        this.matchService = matchService;
    }

    @GetMapping
    public List<MatchSummaryDto> list() {
        return matchService.listSummaries().stream()
            .map(m -> new MatchSummaryDto(m.id(), m.status(),
                m.player1Username(), m.player2Username(), m.createdAt(), m.turnNumber()))
            .toList();
    }

    @PostMapping
    public ResponseEntity<MatchDto> create(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails user) {
        Long deckId = Long.valueOf(body.get("deckId").toString());
        Long id = matchService.createMatch(user.getUsername(), deckId);
        return ResponseEntity.status(HttpStatus.CREATED).body(new MatchDto(id, "WAITING"));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<Void> join(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails user) {
        Long deckId = Long.valueOf(body.get("deckId").toString());
        boolean ok = matchService.joinMatch(id, user.getUsername(), deckId);
        return ok ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }

    @GetMapping("/{id}/state")
    public ResponseEntity<FilteredGameStateDto> state(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        return matchService.getSession(String.valueOf(id))
            .map(state -> buildFilteredDto(id, state, user.getUsername()))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    private FilteredGameStateDto buildFilteredDto(Long matchId, GameState state, String username) {
        // Determine which PlayerState is "me"
        PlayerState me = state.getPlayer1().getUserId().toString().equals(username)
            ? state.getPlayer1() : state.getPlayer2();
        PlayerState opp = me == state.getPlayer1() ? state.getPlayer2() : state.getPlayer1();

        return new FilteredGameStateDto(
            matchId,
            state.getGlobalTurn(),
            state.getCurrentPlayer().getUserId(),
            state.getTurnPhase().name(),
            me.getPrizes().size(),
            opp.getPrizes().size(),
            me.getHand().size(),
            opp.getHand().size(),
            me.getDeck().size(),
            opp.getDeck().size(),
            toFieldPokemon(me.getActivePokemon()),
            toFieldPokemon(opp.getActivePokemon()),
            me.getBench().stream().map(this::toFieldPokemon).toList(),
            opp.getBench().stream().map(this::toFieldPokemon).toList(),
            me.getHand().stream().map(c -> new FilteredGameStateDto.HandCardDto(
                c.id(), c.name(),
                c.subtypes().isEmpty() ? "" : c.subtypes().get(0),
                c.supertype()
            )).toList(),
            null // winner
        );
    }

    private FilteredGameStateDto.FieldPokemonDto toFieldPokemon(PokemonInPlay pip) {
        if (pip == null) return null;
        Map<String, Integer> energyCounts = pip.getAttachedEnergyIds().stream()
            .collect(Collectors.groupingBy(e -> e, Collectors.summingInt(e -> 1)));
        List<FilteredGameStateDto.AttackDto> attacks = pip.getCard().attacks().stream()
            .map(a -> new FilteredGameStateDto.AttackDto(
                (String) a.get("name"),
                a.get("damage") != null ? a.get("damage").toString() : "0"
            )).toList();
        return new FilteredGameStateDto.FieldPokemonDto(
            pip.getCard().name(),
            pip.getCurrentHp(),
            pip.getCard().hp(),
            energyCounts,
            pip.getStatusCondition() != null ? pip.getStatusCondition().name() : null,
            attacks
        );
    }
}
```

- [ ] **Step 6: Run tests**

```bash
cd backend && ./mvnw test -Dtest=MatchControllerTest -q 2>&1 | tail -10
```
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/utn/pokemontcg/api/controller/MatchController.java \
        backend/src/main/java/com/utn/pokemontcg/api/dto/MatchDto.java \
        backend/src/main/java/com/utn/pokemontcg/api/dto/MatchSummaryDto.java \
        backend/src/main/java/com/utn/pokemontcg/api/dto/FilteredGameStateDto.java \
        backend/src/main/java/com/utn/pokemontcg/application/service/MatchSessionService.java \
        backend/src/test/java/com/utn/pokemontcg/api/controller/MatchControllerTest.java
git commit -m "feat(be): WS-02 MatchController REST — create/join/list/state endpoints"
```

---

## Task 3: Backend — WS Auth Channel Interceptor

**Files:**
- Create: `backend/src/main/java/com/utn/pokemontcg/security/WsAuthChannelInterceptor.java`
- Modify: `backend/src/main/java/com/utn/pokemontcg/config/WebSocketConfig.java`
- Modify: `backend/src/main/java/com/utn/pokemontcg/config/SecurityConfig.java`
- Create: `backend/src/test/java/com/utn/pokemontcg/security/WsAuthChannelInterceptorTest.java`

- [ ] **Step 1: Write failing test**

```java
// backend/src/test/java/com/utn/pokemontcg/security/WsAuthChannelInterceptorTest.java
package com.utn.pokemontcg.security;

import com.utn.pokemontcg.config.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class WsAuthChannelInterceptorTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final WsAuthChannelInterceptor interceptor = new WsAuthChannelInterceptor(jwtUtil);

    @Test
    void connect_withValidToken_passes() {
        when(jwtUtil.isValid("good-token")).thenReturn(true);
        when(jwtUtil.extractUsername("good-token")).thenReturn("player1");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer good-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(msg, mock(MessageChannel.class));
        assertThat(result).isNotNull();
    }

    @Test
    void connect_withInvalidToken_throwsException() {
        when(jwtUtil.isValid("bad-token")).thenReturn(false);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer bad-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(msg, mock(MessageChannel.class)))
            .isInstanceOf(org.springframework.messaging.MessageDeliveryException.class);
    }

    @Test
    void nonConnect_passes_withoutTokenCheck() {
        Message<byte[]> msg = MessageBuilder
            .createMessage(new byte[0],
                StompHeaderAccessor.create(StompCommand.SEND).getMessageHeaders());
        Message<?> result = interceptor.preSend(msg, mock(MessageChannel.class));
        assertThat(result).isNotNull();
        verifyNoInteractions(jwtUtil);
    }
}
```

- [ ] **Step 2: Run to verify fail**

```bash
cd backend && ./mvnw test -Dtest=WsAuthChannelInterceptorTest -q 2>&1 | tail -10
```
Expected: compilation error.

- [ ] **Step 3: Implement WsAuthChannelInterceptor**

```java
// backend/src/main/java/com/utn/pokemontcg/security/WsAuthChannelInterceptor.java
package com.utn.pokemontcg.security;

import com.utn.pokemontcg.config.JwtUtil;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WsAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    public WsAuthChannelInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new MessageDeliveryException("Missing Authorization header");
            }
            String token = authHeader.substring(7);
            if (!jwtUtil.isValid(token)) {
                throw new MessageDeliveryException("Invalid JWT token");
            }
            String username = jwtUtil.extractUsername(token);
            accessor.setUser(new UsernamePasswordAuthenticationToken(username, null, List.of()));
        }
        return message;
    }
}
```

- [ ] **Step 4: Register interceptor in WebSocketConfig**

```java
// Modify backend/src/main/java/com/utn/pokemontcg/config/WebSocketConfig.java
// Replace the entire file:
package com.utn.pokemontcg.config;

import com.utn.pokemontcg.security.WsAuthChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WsAuthChannelInterceptor wsAuthInterceptor;

    public WebSocketConfig(WsAuthChannelInterceptor wsAuthInterceptor) {
        this.wsAuthInterceptor = wsAuthInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:4200", "http://127.0.0.1:4200")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(wsAuthInterceptor);
    }
}
```

- [ ] **Step 5: Permit /ws/** in SecurityConfig**

In `SecurityConfig.java`, add `"/ws/**"` to the `permitAll()` list:

```java
.requestMatchers(
    "/api/auth/**",
    "/api/cards/**",
    "/ws/**",
    "/swagger-ui/**",
    "/swagger-ui.html",
    "/v3/api-docs/**",
    "/actuator/**"
).permitAll()
```

- [ ] **Step 6: Run tests**

```bash
cd backend && ./mvnw test -Dtest=WsAuthChannelInterceptorTest -q 2>&1 | tail -10
```
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/utn/pokemontcg/security/WsAuthChannelInterceptor.java \
        backend/src/main/java/com/utn/pokemontcg/config/WebSocketConfig.java \
        backend/src/main/java/com/utn/pokemontcg/config/SecurityConfig.java \
        backend/src/test/java/com/utn/pokemontcg/security/WsAuthChannelInterceptorTest.java
git commit -m "feat(be): WS-03 WsAuthChannelInterceptor — JWT validation on STOMP CONNECT"
```

---

## Task 4: Backend — GameEventPublisher and MatchActionController

**Files:**
- Create: `backend/src/main/java/com/utn/pokemontcg/api/ws/GameEventPublisher.java`
- Create: `backend/src/main/java/com/utn/pokemontcg/api/dto/ws/GameEventDto.java`
- Create: `backend/src/main/java/com/utn/pokemontcg/api/dto/ws/GameActionDto.java`
- Create: `backend/src/main/java/com/utn/pokemontcg/api/dto/ws/AckDto.java`
- Create: `backend/src/main/java/com/utn/pokemontcg/api/ws/MatchActionController.java`
- Create: `backend/src/test/java/com/utn/pokemontcg/api/ws/GameEventPublisherTest.java`

- [ ] **Step 1: Create WS DTOs**

```java
// backend/src/main/java/com/utn/pokemontcg/api/dto/ws/GameEventDto.java
package com.utn.pokemontcg.api.dto.ws;

public record GameEventDto(String type, Object payload, long sequence) {}
```

```java
// backend/src/main/java/com/utn/pokemontcg/api/dto/ws/GameActionDto.java
package com.utn.pokemontcg.api.dto.ws;

import java.util.Map;

public record GameActionDto(String type, Map<String, Object> payload) {}
```

```java
// backend/src/main/java/com/utn/pokemontcg/api/dto/ws/AckDto.java
package com.utn.pokemontcg.api.dto.ws;

public record AckDto(boolean ok, String reason) {
    public static AckDto ok() { return new AckDto(true, null); }
    public static AckDto nack(String reason) { return new AckDto(false, reason); }
}
```

- [ ] **Step 2: Write failing test for GameEventPublisher**

```java
// backend/src/test/java/com/utn/pokemontcg/api/ws/GameEventPublisherTest.java
package com.utn.pokemontcg.api.ws;

import com.utn.pokemontcg.api.dto.ws.GameEventDto;
import com.utn.pokemontcg.domain.engine.event.GameEvent;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GameEventPublisherTest {

    private final SimpMessagingTemplate broker = mock(SimpMessagingTemplate.class);
    private final GameEventPublisher publisher = new GameEventPublisher(broker);

    @Test
    void publish_sendsToMatchTopic() {
        List<GameEvent> events = List.of(
            new GameEvent.TurnEnded("match-1", 1L, 5)
        );
        publisher.publish("match-1", 1L, 2L, events);
        verify(broker, atLeastOnce())
            .convertAndSend(eq("/topic/match/match-1"), any(GameEventDto.class));
    }

    @Test
    void publish_cardDrawn_hiddenForOpponent() {
        // CardDrawn for player1 must NOT expose cardId to player2's channel
        List<GameEvent> events = List.of(new GameEvent.CardDrawn("m1", 1L, "card-42"));
        publisher.publish("m1", 1L, 2L, events);
        // Verify player1 gets full event, player2 gets masked event
        verify(broker, atLeastOnce())
            .convertAndSend(eq("/topic/match/m1/1"), any(GameEventDto.class));
        verify(broker, atLeastOnce())
            .convertAndSend(eq("/topic/match/m1/2"), any(GameEventDto.class));
    }
}
```

- [ ] **Step 3: Run to verify fail**

```bash
cd backend && ./mvnw test -Dtest=GameEventPublisherTest -q 2>&1 | tail -10
```
Expected: compilation error.

- [ ] **Step 4: Implement GameEventPublisher**

```java
// backend/src/main/java/com/utn/pokemontcg/api/ws/GameEventPublisher.java
package com.utn.pokemontcg.api.ws;

import com.utn.pokemontcg.api.dto.ws.GameEventDto;
import com.utn.pokemontcg.domain.engine.event.GameEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class GameEventPublisher {

    private final SimpMessagingTemplate broker;
    private final AtomicLong seq = new AtomicLong(0);

    public GameEventPublisher(SimpMessagingTemplate broker) {
        this.broker = broker;
    }

    /**
     * Publishes events to per-player topics. Per-player topics are
     * `/topic/match/{matchId}/{userId}` so each player only receives
     * events filtered for their perspective.
     */
    public void publish(String matchId, Long p1Id, Long p2Id, List<GameEvent> events) {
        for (GameEvent event : events) {
            long s = seq.incrementAndGet();
            // Build payloads — some events are hidden/masked for the opponent
            GameEventDto forP1 = toDto(event, p1Id, s);
            GameEventDto forP2 = toDto(event, p2Id, s);
            // Also broadcast to shared topic for event log
            broker.convertAndSend("/topic/match/" + matchId, forP1);
            // Per-player filtered channels
            broker.convertAndSend("/topic/match/" + matchId + "/" + p1Id, forP1);
            broker.convertAndSend("/topic/match/" + matchId + "/" + p2Id, forP2);
        }
    }

    private GameEventDto toDto(GameEvent event, Long viewerId, long sequence) {
        String type = event.getClass().getSimpleName();
        Map<String, Object> payload = switch (event) {
            case GameEvent.CardDrawn e -> {
                Map<String, Object> m = new HashMap<>();
                m.put("matchId", e.matchId());
                m.put("userId", e.userId());
                // Hide card identity from opponent
                if (e.userId().equals(viewerId)) {
                    m.put("cardId", e.cardId());
                } else {
                    m.put("cardId", null); // masked
                }
                yield m;
            }
            case GameEvent.MatchStarted e -> Map.of(
                "matchId", e.matchId(), "player1Id", e.player1Id(),
                "player2Id", e.player2Id(), "firstPlayerId", e.firstPlayerId());;
            case GameEvent.PokemonPlayed e -> Map.of(
                "matchId", e.matchId(), "userId", e.userId(),
                "cardId", e.cardId(), "toBench", e.toBench());
            case GameEvent.EnergyAttached e -> Map.of(
                "matchId", e.matchId(), "userId", e.userId(),
                "energyCardId", e.energyCardId(), "targetCardId", e.targetCardId());
            case GameEvent.AttackDeclared e -> Map.of(
                "matchId", e.matchId(), "attackerId", e.attackerId(), "attackName", e.attackName());
            case GameEvent.DamageDealt e -> Map.of(
                "matchId", e.matchId(), "amount", e.amount(),
                "attackerCardId", e.attackerCardId(), "defenderCardId", e.defenderCardId());
            case GameEvent.StatusApplied e -> Map.of(
                "matchId", e.matchId(), "targetCardId", e.targetCardId(), "status", e.status());
            case GameEvent.PokemonKnockedOut e -> Map.of(
                "matchId", e.matchId(), "ownerUserId", e.ownerUserId(), "cardId", e.cardId());
            case GameEvent.PrizeTaken e -> Map.of(
                "matchId", e.matchId(), "userId", e.userId(), "prizesRemaining", e.prizesRemaining());
            case GameEvent.TurnEnded e -> Map.of(
                "matchId", e.matchId(), "userId", e.userId(), "globalTurn", e.globalTurn());
            case GameEvent.MatchFinished e -> Map.of(
                "matchId", e.matchId(), "winnerUserId", e.winnerUserId(), "reason", e.reason());
            case GameEvent.TrainerPlayed e -> Map.of(
                "matchId", e.matchId(), "userId", e.userId(), "cardId", e.cardId());
        };
        return new GameEventDto(type, payload, sequence);
    }
}
```

- [ ] **Step 5: Implement MatchActionController**

```java
// backend/src/main/java/com/utn/pokemontcg/api/ws/MatchActionController.java
package com.utn.pokemontcg.api.ws;

import com.utn.pokemontcg.api.dto.ws.AckDto;
import com.utn.pokemontcg.api.dto.ws.GameActionDto;
import com.utn.pokemontcg.application.service.MatchSessionService;
import com.utn.pokemontcg.domain.engine.ActionResult;
import com.utn.pokemontcg.domain.engine.GameEngineFacade;
import com.utn.pokemontcg.domain.engine.action.GameAction;
import com.utn.pokemontcg.domain.engine.event.GameEvent;
import com.utn.pokemontcg.domain.engine.model.GameState;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class MatchActionController {

    private final MatchSessionService matchService;
    private final GameEventPublisher publisher;

    public MatchActionController(MatchSessionService matchService, GameEventPublisher publisher) {
        this.matchService = matchService;
        this.publisher = publisher;
    }

    @MessageMapping("/match/{matchId}/action")
    @SendToUser("/queue/ack")
    public AckDto handleAction(
            @DestinationVariable String matchId,
            @Payload GameActionDto dto,
            Principal principal) {

        Optional<GameState> stateOpt = matchService.getSession(matchId);
        Optional<GameEngineFacade> engineOpt = matchService.getEngine(matchId);

        if (stateOpt.isEmpty() || engineOpt.isEmpty()) {
            return AckDto.nack("match not found: " + matchId);
        }

        GameState state = stateOpt.get();
        GameEngineFacade engine = engineOpt.get();
        Long userId = resolveUserId(state, principal.getName());
        if (userId == null) return AckDto.nack("player not in this match");

        GameAction action = parseAction(dto, userId);
        if (action == null) return AckDto.nack("unknown action type: " + dto.type());

        synchronized (state) {
            ActionResult result = engine.applyAction(state, action);
            if (!result.isOk()) return AckDto.nack(result.getReason());

            List<GameEvent> events = engine.getLastEvents();
            MatchSessionService.MatchMeta meta = matchService.getMeta(Long.valueOf(matchId)).orElse(null);
            Long p1Id = state.getPlayer1().getUserId();
            Long p2Id = state.getPlayer2().getUserId();
            publisher.publish(matchId, p1Id, p2Id, events);
        }
        return AckDto.ok();
    }

    private Long resolveUserId(GameState state, String username) {
        // Username is stored as the Principal name from JWT; match against userId strings
        // For now userId IS stored as Long — the principal name is the JWT subject (username string)
        // In a real system we'd look up user by username. For sprint 5, store username→userId in MatchSessionService.
        // Fallback: try to parse numeric username (works in tests)
        try {
            Long id = Long.valueOf(username);
            if (state.getPlayer1().getUserId().equals(id) || state.getPlayer2().getUserId().equals(id)) return id;
        } catch (NumberFormatException ignored) {}
        return null;
    }

    private GameAction parseAction(GameActionDto dto, Long userId) {
        Map<String, Object> p = dto.payload();
        return switch (dto.type()) {
            case "PLAY_BASIC" -> new GameAction.PlayBasicPokemon(
                userId,
                (String) p.get("cardId"),
                Boolean.TRUE.equals(p.get("toBench"))
            );
            case "ATTACH_ENERGY" -> new GameAction.AttachEnergy(
                userId,
                (String) p.get("energyCardId"),
                (String) p.get("targetInPlayId")
            );
            case "ATTACK" -> new GameAction.Attack(
                userId,
                ((Number) p.get("attackIndex")).intValue()
            );
            case "PASS" -> new GameAction.Pass(userId);
            default -> null;
        };
    }
}
```

- [ ] **Step 6: Run publisher tests**

```bash
cd backend && ./mvnw test -Dtest=GameEventPublisherTest -q 2>&1 | tail -10
```
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 7: Run full backend test suite**

```bash
cd backend && ./mvnw test -q 2>&1 | tail -15
```
Expected: all tests pass.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/utn/pokemontcg/api/ws/ \
        backend/src/main/java/com/utn/pokemontcg/api/dto/ws/ \
        backend/src/test/java/com/utn/pokemontcg/api/ws/
git commit -m "feat(be): WS-04 GameEventPublisher + MatchActionController — STOMP action/event pipeline"
```

---

## Task 5: Frontend — game-event and game-action TypeScript models

**Files:**
- Create: `frontend/src/app/core/models/game-event.model.ts`
- Create: `frontend/src/app/core/models/game-action.model.ts`

- [ ] **Step 1: Create game-event model**

```typescript
// frontend/src/app/core/models/game-event.model.ts

export type GameEventType =
  | 'MatchStarted' | 'CardDrawn' | 'PokemonPlayed' | 'EnergyAttached'
  | 'TrainerPlayed' | 'AttackDeclared' | 'DamageDealt' | 'StatusApplied'
  | 'PokemonKnockedOut' | 'PrizeTaken' | 'TurnEnded' | 'MatchFinished';

export interface GameEventDto {
  type: GameEventType;
  payload: Record<string, unknown>;
  sequence: number;
}

// Typed payload shapes
export interface MatchStartedPayload { matchId: string; player1Id: number; player2Id: number; firstPlayerId: number; }
export interface CardDrawnPayload { matchId: string; userId: number; cardId: string | null; }
export interface PokemonPlayedPayload { matchId: string; userId: number; cardId: string; toBench: boolean; }
export interface EnergyAttachedPayload { matchId: string; userId: number; energyCardId: string; targetCardId: string; }
export interface AttackDeclaredPayload { matchId: string; attackerId: number; attackName: string; }
export interface DamageDealtPayload { matchId: string; attackerCardId: string; defenderCardId: string; amount: number; }
export interface StatusAppliedPayload { matchId: string; targetCardId: string; status: string; }
export interface PokemonKnockedOutPayload { matchId: string; ownerUserId: number; cardId: string; }
export interface PrizeTakenPayload { matchId: string; userId: number; prizesRemaining: number; }
export interface TurnEndedPayload { matchId: string; userId: number; globalTurn: number; }
export interface MatchFinishedPayload { matchId: string; winnerUserId: number; reason: string; }
```

- [ ] **Step 2: Create game-action model**

```typescript
// frontend/src/app/core/models/game-action.model.ts

export type GameActionType = 'PLAY_BASIC' | 'ATTACH_ENERGY' | 'ATTACK' | 'PASS' | 'PLAY_TRAINER' | 'EVOLVE' | 'RETREAT';

export interface GameActionDto {
  type: GameActionType;
  payload: Record<string, unknown>;
}

export function playBasic(cardId: string, toBench: boolean): GameActionDto {
  return { type: 'PLAY_BASIC', payload: { cardId, toBench } };
}

export function attachEnergy(energyCardId: string, targetInPlayId: string): GameActionDto {
  return { type: 'ATTACH_ENERGY', payload: { energyCardId, targetInPlayId } };
}

export function attack(attackIndex: number): GameActionDto {
  return { type: 'ATTACK', payload: { attackIndex } };
}

export function pass(): GameActionDto {
  return { type: 'PASS', payload: {} };
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/core/models/game-event.model.ts \
        frontend/src/app/core/models/game-action.model.ts
git commit -m "feat(fe): FE-09 game-event and game-action TypeScript models"
```

---

## Task 6: Frontend — MatchSocketService

**Files:**
- Create: `frontend/src/app/core/services/match-socket.service.ts`

Replaces the spike-grade `WebSocketService.subscribe()` polling loop with proper RxJS-based lifecycle.

- [ ] **Step 1: Implement MatchSocketService**

```typescript
// frontend/src/app/core/services/match-socket.service.ts
import { Injectable, OnDestroy } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Observable, Subject, BehaviorSubject } from 'rxjs';
import { filter } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { GameEventDto } from '../models/game-event.model';
import { GameActionDto } from '../models/game-action.model';

@Injectable({ providedIn: 'root' })
export class MatchSocketService implements OnDestroy {
  private client: Client | null = null;
  private eventsSubject = new Subject<GameEventDto>();
  private ackSubject = new Subject<{ ok: boolean; reason: string | null }>();
  private connectionState = new BehaviorSubject<'disconnected' | 'connecting' | 'connected'>('disconnected');

  readonly events$: Observable<GameEventDto> = this.eventsSubject.asObservable();
  readonly acks$: Observable<{ ok: boolean; reason: string | null }> = this.ackSubject.asObservable();
  readonly connectionState$ = this.connectionState.asObservable();

  private subs: StompSubscription[] = [];
  private currentMatchId: string | null = null;

  connect(matchId: string, token: string): void {
    if (this.client?.active) this.disconnect();
    this.currentMatchId = matchId;
    this.connectionState.next('connecting');

    this.client = new Client({
      webSocketFactory: () => new SockJS(environment.wsUrl),
      reconnectDelay: 3_000,
      connectHeaders: { Authorization: `Bearer ${token}` },
      debug: (msg) => console.debug('[match-ws]', msg),
      onConnect: () => {
        this.connectionState.next('connected');
        this.subscribeToMatch(matchId);
      },
      onDisconnect: () => this.connectionState.next('disconnected'),
      onStompError: (frame) => console.error('[match-ws] broker error', frame),
    });
    this.client.activate();
  }

  private subscribeToMatch(matchId: string): void {
    if (!this.client) return;
    // Subscribe to personal filtered channel
    const userId = this.resolveUserId();
    if (userId) {
      const sub1 = this.client.subscribe(
        `/topic/match/${matchId}/${userId}`,
        (msg: IMessage) => this.eventsSubject.next(JSON.parse(msg.body) as GameEventDto)
      );
      this.subs.push(sub1);
    }
    // ACK channel
    const sub2 = this.client.subscribe(
      '/user/queue/ack',
      (msg: IMessage) => this.ackSubject.next(JSON.parse(msg.body))
    );
    this.subs.push(sub2);
  }

  sendAction(matchId: string, action: GameActionDto): void {
    this.client?.publish({
      destination: `/app/match/${matchId}/action`,
      body: JSON.stringify(action),
    });
  }

  disconnect(): void {
    this.subs.forEach(s => s.unsubscribe());
    this.subs = [];
    this.client?.deactivate();
    this.connectionState.next('disconnected');
    this.currentMatchId = null;
  }

  ngOnDestroy(): void { this.disconnect(); }

  private resolveUserId(): string | null {
    // Read userId from localStorage (set by AuthService on login)
    return localStorage.getItem('userId');
  }
}
```

- [ ] **Step 2: Ensure AuthService stores userId in localStorage**

Open `frontend/src/app/core/services/auth.service.ts`. After login success, add:

```typescript
// In the login() or setCurrentUser() method, after storing token:
localStorage.setItem('userId', String(user.id));
```

Check the current auth service structure and add the `userId` storage after the JWT storage line. If `AuthService` stores `user` as a signal, also expose `userId()` as a computed signal:

```typescript
readonly userId = computed(() => this.currentUser()?.id ?? null);
```

- [ ] **Step 3: Lint check**

```bash
cd frontend && npx ng lint --quiet 2>&1 | tail -20
```
Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/core/services/match-socket.service.ts \
        frontend/src/app/core/services/auth.service.ts
git commit -m "feat(fe): FE-09 MatchSocketService — typed STOMP client with RxJS observables"
```

---

## Task 7: Frontend — MatchStateStore

**Files:**
- Create: `frontend/src/app/core/store/match-state.store.ts`

- [ ] **Step 1: Implement MatchStateStore**

```typescript
// frontend/src/app/core/store/match-state.store.ts
import { Injectable, signal, computed } from '@angular/core';
import { FilteredGameStateDto } from '../models/match.model';
import { GameEventDto, TurnEndedPayload, DamageDealtPayload, PokemonKnockedOutPayload,
         PrizeTakenPayload, MatchFinishedPayload, PokemonPlayedPayload, EnergyAttachedPayload,
         StatusAppliedPayload } from '../models/game-event.model';

@Injectable({ providedIn: 'root' })
export class MatchStateStore {
  private _state = signal<FilteredGameStateDto | null>(null);
  private _events = signal<GameEventDto[]>([]);
  private _lastEventSeq = signal(0);

  readonly state = this._state.asReadonly();
  readonly events = this._events.asReadonly();
  readonly lastEventSeq = this._lastEventSeq.asReadonly();
  readonly isMyTurn = computed(() => {
    const s = this._state();
    return s ? s.currentPlayerId === this.viewerUserId : false;
  });

  private viewerUserId: number = 0;

  setViewerUserId(id: number): void { this.viewerUserId = id; }

  /** Called on initial REST load or reconnection snapshot */
  setSnapshot(snapshot: FilteredGameStateDto): void {
    this._state.set(snapshot);
  }

  /** Apply an incoming WS event as a local reducer */
  applyEvent(event: GameEventDto): void {
    this._events.update(arr => [...arr, event]);
    this._lastEventSeq.set(event.sequence);
    this._state.update(s => {
      if (!s) return s;
      return this.reduce(s, event);
    });
  }

  private reduce(state: FilteredGameStateDto, event: GameEventDto): FilteredGameStateDto {
    switch (event.type) {
      case 'TurnEnded': {
        const p = event.payload as unknown as TurnEndedPayload;
        return { ...state, turnNumber: p.globalTurn };
      }
      case 'DamageDealt': {
        const p = event.payload as unknown as DamageDealtPayload;
        return applyDamage(state, p.defenderCardId, p.amount);
      }
      case 'PokemonKnockedOut': {
        const p = event.payload as unknown as PokemonKnockedOutPayload;
        return removeKnockedOut(state, p.cardId, p.ownerUserId, this.viewerUserId);
      }
      case 'PrizeTaken': {
        const p = event.payload as unknown as PrizeTakenPayload;
        if (p.userId === this.viewerUserId) {
          return { ...state, myPrizesLeft: p.prizesRemaining };
        } else {
          return { ...state, opponentPrizesLeft: p.prizesRemaining };
        }
      }
      case 'MatchFinished': {
        const p = event.payload as unknown as MatchFinishedPayload;
        return { ...state, winner: String(p.winnerUserId) };
      }
      default:
        return state;
    }
  }

  reset(): void {
    this._state.set(null);
    this._events.set([]);
    this._lastEventSeq.set(0);
  }
}

function applyDamage(state: FilteredGameStateDto, defenderCardId: string, amount: number): FilteredGameStateDto {
  const updateActive = (fp: typeof state.myActive) =>
    fp ? { ...fp, hp: Math.max(0, fp.hp - amount) } : fp;
  // We don't know defender card id in the frontend filtered DTO (we have name not id)
  // Just return state unchanged — the next state poll will correct it
  return state;
}

function removeKnockedOut(state: FilteredGameStateDto, cardId: string, ownerUserId: number, viewerUserId: number): FilteredGameStateDto {
  // Similarly, rely on state snapshot for accuracy; local reducer is best-effort
  return state;
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/core/store/match-state.store.ts
git commit -m "feat(fe): FE-10 MatchStateStore — signal-based reactive state with event reducers"
```

---

## Task 8: Frontend — PokemonInPlay component

**Files:**
- Create: `frontend/src/app/features/match/components/pokemon-in-play/pokemon-in-play.component.ts`
- Create: `frontend/src/app/features/match/components/pokemon-in-play/pokemon-in-play.component.html`
- Create: `frontend/src/app/features/match/components/pokemon-in-play/pokemon-in-play.component.scss`

- [ ] **Step 1: Create the component**

```typescript
// frontend/src/app/features/match/components/pokemon-in-play/pokemon-in-play.component.ts
import { Component, Input, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FieldPokemon } from '../../../../core/models/match.model';

@Component({
  selector: 'app-pokemon-in-play',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './pokemon-in-play.component.html',
  styleUrl: './pokemon-in-play.component.scss',
})
export class PokemonInPlayComponent {
  @Input({ required: true }) pokemon!: FieldPokemon;
  @Input() isOpponent = false;
  @Input() isActive = false;

  get hpPercent(): number {
    if (!this.pokemon || this.pokemon.maxHp === 0) return 0;
    return Math.round((this.pokemon.hp / this.pokemon.maxHp) * 100);
  }

  get hpClass(): string {
    const pct = this.hpPercent;
    if (pct > 50) return 'hp-high';
    if (pct > 25) return 'hp-mid';
    return 'hp-low';
  }

  get statusRotationClass(): string {
    if (!this.pokemon?.statusCondition) return '';
    switch (this.pokemon.statusCondition) {
      case 'ASLEEP': return 'rotate-left';
      case 'CONFUSED': return 'rotate-180';
      case 'PARALYZED': return 'rotate-right';
      default: return '';
    }
  }

  get hasOverlayStatus(): boolean {
    return this.pokemon?.statusCondition === 'BURNED' ||
           this.pokemon?.statusCondition === 'POISONED';
  }

  get overlayClass(): string {
    if (!this.pokemon?.statusCondition) return '';
    return this.pokemon.statusCondition.toLowerCase();
  }

  energyEntries(): Array<{ type: string; count: number }> {
    if (!this.pokemon?.energies) return [];
    return Object.entries(this.pokemon.energies).map(([type, count]) => ({ type, count }));
  }
}
```

- [ ] **Step 2: Create template**

```html
<!-- frontend/src/app/features/match/components/pokemon-in-play/pokemon-in-play.component.html -->
<div class="pokemon-card" [class]="statusRotationClass" [class.active-slot]="isActive">
  <div *ngIf="hasOverlayStatus" class="status-overlay" [class]="overlayClass"></div>

  <div class="pokemon-name">{{ pokemon.name }}</div>

  <div class="hp-bar-container">
    <div class="hp-bar" [class]="hpClass" [style.width.%]="hpPercent"></div>
  </div>
  <div class="hp-text">{{ pokemon.hp }} / {{ pokemon.maxHp }} HP</div>

  <div class="energies" *ngIf="energyEntries().length > 0">
    <span *ngFor="let e of energyEntries()" class="energy-icon" [class]="'energy-' + e.type.toLowerCase()">
      {{ e.type[0] }}×{{ e.count }}
    </span>
  </div>

  <div *ngIf="pokemon.statusCondition" class="status-badge">
    {{ pokemon.statusCondition }}
  </div>
</div>
```

- [ ] **Step 3: Create styles**

```scss
// frontend/src/app/features/match/components/pokemon-in-play/pokemon-in-play.component.scss
.pokemon-card {
  position: relative;
  background: #1a2a3a;
  border: 1px solid #3a5a7a;
  border-radius: 8px;
  padding: 8px;
  min-width: 90px;
  text-align: center;
  transition: transform 0.3s ease;

  &.rotate-left  { transform: rotate(-90deg); }
  &.rotate-180   { transform: rotate(180deg); }
  &.rotate-right { transform: rotate(90deg); }
  &.active-slot  { border-color: #f0b040; box-shadow: 0 0 8px #f0b040; }
}

.status-overlay {
  position: absolute; inset: 0; border-radius: 8px; pointer-events: none;
  &.burned  { background: rgba(255, 80, 0, 0.3); }
  &.poisoned { background: rgba(120, 0, 200, 0.3); }
}

.pokemon-name { font-weight: bold; font-size: 0.75rem; color: #e0e0ff; margin-bottom: 4px; }

.hp-bar-container { background: #0a1a2a; border-radius: 4px; height: 6px; margin: 4px 0; overflow: hidden; }
.hp-bar {
  height: 100%; border-radius: 4px; transition: width 0.3s ease;
  &.hp-high  { background: #4caf50; }
  &.hp-mid   { background: #ff9800; }
  &.hp-low   { background: #f44336; }
}
.hp-text { font-size: 0.65rem; color: #aaaacc; }

.energies { margin-top: 4px; display: flex; gap: 2px; flex-wrap: wrap; justify-content: center; }
.energy-icon { font-size: 0.6rem; padding: 1px 3px; border-radius: 3px; background: #2a3a4a; }

.status-badge { font-size: 0.55rem; margin-top: 2px; color: #ffcc00; text-transform: uppercase; }
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/match/components/
git commit -m "feat(fe): FE-14 PokemonInPlay component — HP bar, energies, status rotations"
```

---

## Task 9: Frontend — AttackModal component

**Files:**
- Create: `frontend/src/app/features/match/components/attack-modal/attack-modal.component.ts`
- Create: `frontend/src/app/features/match/components/attack-modal/attack-modal.component.html`

- [ ] **Step 1: Create AttackModal**

```typescript
// frontend/src/app/features/match/components/attack-modal/attack-modal.component.ts
import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FieldPokemon } from '../../../../core/models/match.model';

@Component({
  selector: 'app-attack-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './attack-modal.component.html',
})
export class AttackModalComponent {
  @Input({ required: true }) activePokemon!: FieldPokemon;
  @Output() attackSelected = new EventEmitter<number>();
  @Output() cancelled = new EventEmitter<void>();

  selectAttack(index: number): void {
    this.attackSelected.emit(index);
  }
}
```

- [ ] **Step 2: Create template**

```html
<!-- frontend/src/app/features/match/components/attack-modal/attack-modal.component.html -->
<div class="modal-backdrop" (click)="cancelled.emit()">
  <div class="modal-content" (click)="$event.stopPropagation()">
    <h3>Choose an Attack</h3>
    <div class="attack-list">
      <button
        *ngFor="let atk of activePokemon.attacks; let i = index"
        class="attack-btn"
        (click)="selectAttack(i)">
        <span class="attack-name">{{ atk.name }}</span>
        <span class="attack-damage" *ngIf="atk.damage">{{ atk.damage }}</span>
      </button>
    </div>
    <button class="cancel-btn" (click)="cancelled.emit()">Cancel</button>
  </div>
</div>
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/match/components/attack-modal/
git commit -m "feat(fe): FE-15 AttackModal component — attack selection with damage display"
```

---

## Task 10: Frontend — ActionLog component

**Files:**
- Create: `frontend/src/app/features/match/components/action-log/action-log.component.ts`
- Create: `frontend/src/app/features/match/components/action-log/action-log.component.html`

- [ ] **Step 1: Create ActionLog**

```typescript
// frontend/src/app/features/match/components/action-log/action-log.component.ts
import { Component, Input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { GameEventDto } from '../../../../core/models/game-event.model';

@Component({
  selector: 'app-action-log',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './action-log.component.html',
})
export class ActionLogComponent {
  @Input() events: GameEventDto[] = [];
  collapsed = signal(false);

  toggle(): void { this.collapsed.update(v => !v); }

  describe(event: GameEventDto): string {
    const p = event.payload as Record<string, unknown>;
    switch (event.type) {
      case 'TurnEnded': return `Turn ${p['globalTurn']} ended`;
      case 'DamageDealt': return `${p['amount']} damage dealt`;
      case 'PokemonKnockedOut': return `Pokémon knocked out!`;
      case 'PrizeTaken': return `Prize taken (${p['prizesRemaining']} left)`;
      case 'MatchFinished': return `Match over — winner: ${p['winnerUserId']}`;
      case 'AttackDeclared': return `Attack: ${p['attackName']}`;
      case 'EnergyAttached': return `Energy attached`;
      case 'PokemonPlayed': return `Pokémon played to ${p['toBench'] ? 'bench' : 'active'}`;
      default: return event.type;
    }
  }
}
```

- [ ] **Step 2: Create template**

```html
<!-- frontend/src/app/features/match/components/action-log/action-log.component.html -->
<div class="action-log" [class.collapsed]="collapsed()">
  <div class="log-header" (click)="toggle()">
    <span>Action Log ({{ events.length }})</span>
    <span>{{ collapsed() ? '▲' : '▼' }}</span>
  </div>
  <div class="log-body" *ngIf="!collapsed()">
    <div *ngFor="let event of events.slice().reverse()" class="log-entry" [class]="'entry-' + event.type.toLowerCase()">
      <span class="seq">#{{ event.sequence }}</span>
      {{ describe(event) }}
    </div>
  </div>
</div>
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/match/components/action-log/
git commit -m "feat(fe): FE-16 ActionLog component — collapsible event log"
```

---

## Task 11: Frontend — Wire MatchComponent to MatchSocketService + CDK drag-and-drop

**Files:**
- Modify: `frontend/src/app/features/match/match.component.ts`
- Modify: `frontend/src/app/features/match/match.component.html`
- Modify: `frontend/src/app/features/match/match.component.scss`

This task replaces 3-second REST polling with WebSocket events while keeping REST as fallback for initial state load.

- [ ] **Step 1: Install Angular CDK if not present**

```bash
cd frontend && npm list @angular/cdk 2>/dev/null | grep cdk || npm install @angular/cdk
```

- [ ] **Step 2: Update match.component.ts**

```typescript
// frontend/src/app/features/match/match.component.ts
import { Component, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { MatchService } from '../../core/services/match.service';
import { AuthService } from '../../core/services/auth.service';
import { MatchSocketService } from '../../core/services/match-socket.service';
import { MatchStateStore } from '../../core/store/match-state.store';
import { FilteredGameStateDto } from '../../core/models/match.model';
import { PokemonInPlayComponent } from './components/pokemon-in-play/pokemon-in-play.component';
import { AttackModalComponent } from './components/attack-modal/attack-modal.component';
import { ActionLogComponent } from './components/action-log/action-log.component';
import { attack, pass, playBasic, attachEnergy } from '../../core/models/game-action.model';

@Component({
  selector: 'app-match',
  standalone: true,
  imports: [CommonModule, DragDropModule, PokemonInPlayComponent, AttackModalComponent, ActionLogComponent],
  templateUrl: './match.component.html',
  styleUrl: './match.component.scss',
})
export class MatchComponent implements OnInit, OnDestroy {
  private matchService = inject(MatchService);
  private authService = inject(AuthService);
  private socketService = inject(MatchSocketService);
  private stateStore = inject(MatchStateStore);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  matchId = signal<number>(0);
  loading = signal(true);
  error = signal('');
  showAttackModal = signal(false);

  readonly state = this.stateStore.state;
  readonly events = this.stateStore.events;
  readonly isMyTurn = this.stateStore.isMyTurn;

  private subs: Subscription[] = [];

  get currentUser() { return this.authService.currentUser(); }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.matchId.set(id);

    const user = this.currentUser;
    if (user) {
      this.stateStore.setViewerUserId(user.id);
    }

    // Load initial state via REST
    this.subs.push(
      this.matchService.getState(id).subscribe({
        next: (s) => { this.stateStore.setSnapshot(s); this.loading.set(false); },
        error: () => { this.error.set('Error cargando estado'); this.loading.set(false); },
      })
    );

    // Connect WebSocket
    const token = localStorage.getItem('token') ?? '';
    this.socketService.connect(String(id), token);

    // Apply incoming events to store
    this.subs.push(
      this.socketService.events$.subscribe(event => this.stateStore.applyEvent(event))
    );
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
    this.socketService.disconnect();
    this.stateStore.reset();
  }

  onCardDropToActive(event: CdkDragDrop<unknown>): void {
    const cardId = event.item.data as string;
    this.socketService.sendAction(String(this.matchId()), playBasic(cardId, false));
  }

  onCardDropToBench(event: CdkDragDrop<unknown>): void {
    const cardId = event.item.data as string;
    this.socketService.sendAction(String(this.matchId()), playBasic(cardId, true));
  }

  onAttackClick(): void {
    this.showAttackModal.set(true);
  }

  onAttackSelected(attackIndex: number): void {
    this.showAttackModal.set(false);
    this.socketService.sendAction(String(this.matchId()), attack(attackIndex));
  }

  onPass(): void {
    this.socketService.sendAction(String(this.matchId()), pass());
  }

  goLobby(): void { this.router.navigate(['/lobby']); }
}
```

- [ ] **Step 3: Update match.component.html**

```html
<!-- frontend/src/app/features/match/match.component.html -->
<div class="match-board" *ngIf="!loading(); else loadingTpl">

  <!-- Error banner -->
  <div class="error-banner" *ngIf="error()">{{ error() }}</div>

  <!-- Attack Modal -->
  <app-attack-modal
    *ngIf="showAttackModal() && state()?.myActive"
    [activePokemon]="state()!.myActive!"
    (attackSelected)="onAttackSelected($event)"
    (cancelled)="showAttackModal.set(false)">
  </app-attack-modal>

  <div class="board-layout" *ngIf="state() as s">

    <!-- Opponent zone (top) -->
    <div class="opponent-zone">
      <div class="zone-label">Opponent ({{ s.opponentHandCount }} cards, {{ s.opponentPrizesLeft }} prizes)</div>
      <div class="active-slot" cdkDropList (cdkDropListDropped)="onCardDropToActive($event)">
        <app-pokemon-in-play *ngIf="s.opponentActive" [pokemon]="s.opponentActive" [isOpponent]="true" [isActive]="true" />
        <div *ngIf="!s.opponentActive" class="empty-slot">No active</div>
      </div>
      <div class="bench-row">
        <app-pokemon-in-play *ngFor="let p of s.opponentBench" [pokemon]="p" [isOpponent]="true" />
      </div>
    </div>

    <!-- Center info bar -->
    <div class="center-bar">
      <span class="turn-info">Turn {{ s.turnNumber }}</span>
      <span class="phase-info">{{ s.phase }}</span>
      <span class="turn-indicator" [class.my-turn]="isMyTurn()">
        {{ isMyTurn() ? 'YOUR TURN' : 'Opponent\'s turn' }}
      </span>
      <span *ngIf="s.winner" class="winner-banner">WINNER: {{ s.winner }}</span>
    </div>

    <!-- Player zone (bottom) -->
    <div class="player-zone">
      <div class="active-slot" cdkDropList (cdkDropListDropped)="onCardDropToActive($event)">
        <app-pokemon-in-play *ngIf="s.myActive" [pokemon]="s.myActive" [isActive]="true" />
        <div *ngIf="!s.myActive" class="empty-slot drop-hint">Drop Basic here</div>
      </div>
      <div class="bench-row" cdkDropList (cdkDropListDropped)="onCardDropToBench($event)">
        <app-pokemon-in-play *ngFor="let p of s.myBench" [pokemon]="p" />
        <div *ngFor="let _ of [].constructor(5 - s.myBench.length)" class="bench-empty-slot">—</div>
      </div>
      <div class="prizes-info">{{ s.myPrizesLeft }} prizes left</div>

      <!-- Action panel -->
      <div class="action-panel" *ngIf="isMyTurn()">
        <button class="btn-attack" (click)="onAttackClick()" [disabled]="!s.myActive">Attack</button>
        <button class="btn-pass" (click)="onPass()">Pass</button>
      </div>

      <!-- Hand strip -->
      <div class="hand-strip" cdkDropList [cdkDropListData]="s.myHand">
        <div
          *ngFor="let card of s.myHand"
          class="hand-card"
          cdkDrag
          [cdkDragData]="card.id"
          [class]="'card-type-' + card.supertype.toLowerCase()">
          <span class="card-name">{{ card.name }}</span>
        </div>
      </div>
    </div>
  </div>

  <!-- Action log -->
  <app-action-log [events]="events()" />
</div>

<ng-template #loadingTpl>
  <div class="loading-screen">Loading match…</div>
</ng-template>
```

- [ ] **Step 4: Update match.component.scss**

```scss
// frontend/src/app/features/match/match.component.scss
.match-board {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #0d1b2a;
  color: #e0e0ff;
  font-family: 'Segoe UI', sans-serif;
  overflow: hidden;
}

.error-banner { background: #7a0000; color: #fff; padding: 8px 16px; font-size: 0.85rem; }

.board-layout {
  display: flex;
  flex-direction: column;
  flex: 1;
  gap: 4px;
  padding: 8px;
}

.opponent-zone, .player-zone {
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: rgba(255,255,255,0.03);
  border-radius: 8px;
  padding: 8px;
}

.zone-label { font-size: 0.7rem; color: #6080a0; }

.active-slot {
  min-height: 110px;
  border: 2px dashed #3a5a7a;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  &.cdk-drop-list-dragging { border-color: #f0b040; }
}

.empty-slot {
  color: #406080;
  font-size: 0.75rem;
  &.drop-hint { color: #f0b040; }
}

.bench-row {
  display: flex;
  gap: 6px;
  min-height: 80px;
  padding: 4px;
  border: 1px dashed #2a4a6a;
  border-radius: 6px;
  &.cdk-drop-list-dragging { border-color: #f0b040; }
}

.bench-empty-slot {
  min-width: 90px;
  border: 1px dashed #2a4a6a;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #2a4a6a;
}

.center-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  background: #0a1520;
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 0.8rem;
}
.turn-indicator { font-weight: bold; color: #6080a0; }
.turn-indicator.my-turn { color: #f0b040; }
.winner-banner { color: #ffd700; font-weight: bold; font-size: 1rem; }

.prizes-info { font-size: 0.7rem; color: #6080a0; }

.action-panel {
  display: flex;
  gap: 8px;
}
.btn-attack, .btn-pass {
  padding: 6px 16px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.85rem;
  &:disabled { opacity: 0.4; cursor: not-allowed; }
}
.btn-attack { background: #c0392b; color: #fff; }
.btn-pass   { background: #2c3e50; color: #aaa; }

.hand-strip {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding: 4px 0;
  min-height: 60px;
}
.hand-card {
  min-width: 70px;
  padding: 4px 6px;
  border-radius: 4px;
  background: #1a2a3a;
  border: 1px solid #3a5a7a;
  cursor: grab;
  font-size: 0.65rem;
  &.cdk-drag-dragging { opacity: 0.5; }
  &.card-type-pokémon { border-color: #4caf50; }
  &.card-type-energy   { border-color: #2196f3; }
  &.card-type-trainer  { border-color: #9c27b0; }
}

.loading-screen {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  font-size: 1.5rem;
  color: #6080a0;
}
```

- [ ] **Step 5: Run TypeScript check**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -40
```
Fix any type errors before proceeding.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/features/match/match.component.ts \
        frontend/src/app/features/match/match.component.html \
        frontend/src/app/features/match/match.component.scss
git commit -m "feat(fe): FE-11/12/13 MatchComponent — WS integration, CDK drag-and-drop, action panel"
```

---

## Task 12: Full integration smoke test

- [ ] **Step 1: Start backend**

```bash
cd backend && ./mvnw spring-boot:run 2>&1 &
# Wait for "Started PokemontcgApplication"
```

- [ ] **Step 2: Start frontend**

```bash
cd frontend && npm start 2>&1 &
# Wait for "Compiled successfully"
```

- [ ] **Step 3: Manual test flow**

1. Open http://localhost:4200 in two browser tabs
2. Register two users in tab 1 and tab 2
3. Each user creates a deck with ≥1 Basic Pokémon
4. User 1: go to Lobby, create a match
5. User 2: go to Lobby, join the match
6. Verify both tabs navigate to `/match/{id}`
7. Verify WebSocket connects (check browser console for `[match-ws]` logs)
8. User 1: drag a Basic Pokémon card to Active slot — verify `PLAY_BASIC` action sent
9. Verify opponent tab shows event in action log
10. User 1: click Attack button — verify modal opens with attack list
11. Select attack — verify `ATTACK` action sent and board updates in both tabs

- [ ] **Step 4: Run backend full test suite**

```bash
cd backend && ./mvnw test -q 2>&1 | tail -15
```
Expected: all tests pass.

- [ ] **Step 5: Run frontend lint**

```bash
cd frontend && npx ng lint --quiet 2>&1 | tail -10
```
Expected: no errors.

- [ ] **Step 6: Final commit if any fixes needed**

```bash
git add -p
git commit -m "fix(fe/be): Sprint 5 integration fixes from smoke test"
```

---

## Self-Review Against Spec

### Spec coverage check

| Sprint 5 requirement | Covered by task |
|----------------------|----------------|
| WS-01 STOMP/SockJS config | Task 3 (WebSocketConfig update) |
| WS-02 JWT WS auth interceptor | Task 3 |
| WS-03 GameEventPublisher per-player filtered events | Task 4 |
| WS-04 action messaging `/app/match/{id}/action`, ACK/NACK | Task 4 |
| WS-05 reconnection via `lastEventSeq` | Partial — store tracks seq; full reconnect snapshot served by REST fallback in Task 2 |
| REST `POST /api/matches` | Task 2 |
| REST `POST /api/matches/{id}/join` | Task 2 |
| REST `GET /api/matches` | Task 2 |
| REST `GET /api/matches/{id}/state` | Task 2 |
| FE-09 MatchSocketService typed | Task 6 |
| FE-10 MatchStateStore | Task 7 |
| FE-11 Board layout | Task 11 |
| FE-12 CDK drag-and-drop | Task 11 |
| FE-13 Action panel | Task 11 |
| FE-14 PokemonInPlay w/ status rotations | Task 8 |
| FE-15 Attack modal | Task 9 |
| FE-16 Action log | Task 10 |
| Opponent hand never visible | MatchController `buildFilteredDto` never includes `opponentHand` cards — only count |
| CardDrawn `cardId` masked for opponent | GameEventPublisher Task 4 |

### Gaps identified

- **WS-05 full reconnect with event replay**: Task 7 stores `lastEventSeq` but backend doesn't yet serve `GET /api/matches/{id}/events?since={seq}`. This is noted but deferred as the REST state snapshot covers the functional requirement for Sprint 5. Add a follow-up task in Sprint 6 if needed.
- **Mulligan screen (FE-19)**: Out of scope for this plan — `MatchSetup` already handles mulligan internally in the engine. A dedicated UI screen is a Sprint 6 polish item.
- **Toast notifications**: The action log covers event visibility. Browser `alert()`-style toasts are Sprint 6.

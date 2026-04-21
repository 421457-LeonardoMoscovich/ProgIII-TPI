# Sprint 2 — Deck Builder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement full Deck Builder feature — JWT auth, card search with GIN indexes, deck CRUD with XY1 validation rules, and Angular split-panel editor.

**Architecture:** Stateless JWT (jjwt 0.12.6, HS256) via Spring Security 6; pure-Java DeckValidator (no Spring context); native SQL card search via pg_trgm. Frontend uses Angular CDK DragDropModule + click fallback, 300ms debounce on live validation.

**Tech Stack:** Spring Boot 3.5, jjwt 0.12.6, Spring Security 6, Flyway V2, Angular 21, @angular/cdk@^21, Angular Reactive Forms, Angular CDK DnD.

---

## File Map

### Backend — New / Modified
| File | Action |
|------|--------|
| `backend/pom.xml` | Add spring-security + jjwt deps |
| `backend/src/main/resources/application.yml` | Add `app.jwt.*` config |
| `backend/src/main/resources/db/migration/V2__gin_indexes.sql` | pg_trgm + GIN indexes |
| `backend/src/main/java/com/utn/pokemontcg/domain/model/User.java` | JPA entity → users table |
| `backend/src/main/java/com/utn/pokemontcg/domain/model/Deck.java` | JPA entity → decks table |
| `backend/src/main/java/com/utn/pokemontcg/domain/model/DeckCard.java` | JPA entity → deck_cards table |
| `backend/src/main/java/com/utn/pokemontcg/domain/model/DeckCardId.java` | Embeddable composite PK |
| `backend/src/main/java/com/utn/pokemontcg/infrastructure/persistence/UserRepository.java` | JPA repo |
| `backend/src/main/java/com/utn/pokemontcg/infrastructure/persistence/DeckRepository.java` | JPA repo |
| `backend/src/main/java/com/utn/pokemontcg/infrastructure/persistence/CardRepository.java` | Add native search query |
| `backend/src/main/java/com/utn/pokemontcg/config/JwtUtil.java` | Generate/validate JWT |
| `backend/src/main/java/com/utn/pokemontcg/security/JwtAuthFilter.java` | OncePerRequestFilter |
| `backend/src/main/java/com/utn/pokemontcg/config/SecurityConfig.java` | Spring Security 6 config |
| `backend/src/main/java/com/utn/pokemontcg/api/dto/AuthRequest.java` | record |
| `backend/src/main/java/com/utn/pokemontcg/api/dto/AuthResponse.java` | record |
| `backend/src/main/java/com/utn/pokemontcg/api/dto/DeckDto.java` | record |
| `backend/src/main/java/com/utn/pokemontcg/api/dto/DeckCardDto.java` | record |
| `backend/src/main/java/com/utn/pokemontcg/api/dto/ValidationResultDto.java` | record |
| `backend/src/main/java/com/utn/pokemontcg/api/controller/AuthController.java` | /api/auth/register + login |
| `backend/src/main/java/com/utn/pokemontcg/api/controller/CardController.java` | Add GET /api/cards/search |
| `backend/src/main/java/com/utn/pokemontcg/api/controller/DeckController.java` | Deck CRUD + validate |
| `backend/src/main/java/com/utn/pokemontcg/domain/service/DeckValidator.java` | Pure Java, no Spring |
| `backend/src/main/java/com/utn/pokemontcg/application/service/DeckService.java` | CRUD + owner enforcement |
| `backend/src/test/java/com/utn/pokemontcg/domain/service/DeckValidatorTest.java` | Unit tests ≥95% |
| `backend/src/test/java/com/utn/pokemontcg/application/service/DeckServiceTest.java` | Mockito tests |
| `backend/src/test/java/com/utn/pokemontcg/api/controller/AuthControllerTest.java` | MockMvc tests |

### Frontend — New / Modified
| File | Action |
|------|--------|
| `frontend/package.json` | Add @angular/cdk@^21 |
| `frontend/src/app/app.config.ts` | Add auth interceptor, CDK |
| `frontend/src/app/app.routes.ts` | Add auth + deck-builder routes |
| `frontend/src/app/core/models/user.model.ts` | AuthRequest/Response/User interfaces |
| `frontend/src/app/core/services/auth.service.ts` | login/register/token management |
| `frontend/src/app/core/interceptors/auth.interceptor.ts` | Attach Bearer token |
| `frontend/src/app/core/guards/auth.guard.ts` | canActivate |
| `frontend/src/app/core/services/card.service.ts` | Add search() method |
| `frontend/src/app/core/services/deck.service.ts` | CRUD + validate |
| `frontend/src/app/features/auth/login/login.component.ts` | Reactive form login |
| `frontend/src/app/features/auth/login/login.component.html` | Login template |
| `frontend/src/app/features/auth/register/register.component.ts` | Reactive form register |
| `frontend/src/app/features/auth/register/register.component.html` | Register template |
| `frontend/src/app/shared/components/card-preview/card-preview.component.ts` | @Input() draggable + showAddButton |
| `frontend/src/app/shared/components/card-preview/card-preview.component.html` | Card image + badge |
| `frontend/src/app/features/deck-builder/deck-builder.component.ts` | Split-panel page component |
| `frontend/src/app/features/deck-builder/deck-builder.component.html` | Catalog left, editor right |
| `frontend/src/app/features/deck-builder/deck-editor/deck-editor.component.ts` | Editor + live validation |
| `frontend/src/app/features/deck-builder/deck-editor/deck-editor.component.html` | Card list + validation UI |

---

## Task 1: Dependencies & JWT Config

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: Add Spring Security and jjwt to pom.xml**

Inside the `<dependencies>` block, before the closing `</dependencies>` tag:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

- [ ] **Step 2: Add JWT config to application.yml**

Append to `application.yml`:

```yaml
app:
  jwt:
    secret: "dev-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm"
    expiration-ms: 86400000
```

- [ ] **Step 3: Verify Maven resolves dependencies**

```bash
cd backend && ./mvnw dependency:resolve -q
```
Expected: BUILD SUCCESS (no errors)

- [ ] **Step 4: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/application.yml
git commit -m "feat(sprint-2): add spring-security and jjwt dependencies"
```

---

## Task 2: Flyway V2 — GIN Indexes for Card Search

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__gin_indexes.sql`

- [ ] **Step 1: Create the migration file**

```sql
-- Sprint 2 / BE-05: GIN indexes para búsqueda de cartas con pg_trgm
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_cards_name_trgm     ON cards USING gin(name gin_trgm_ops);
CREATE INDEX idx_cards_subtypes_gin  ON cards USING gin(subtypes);
CREATE INDEX idx_cards_supertype_btree ON cards(supertype);
```

Note: `idx_cards_supertype` already exists from V1. The btree index is a no-op if exists; use a different name or drop first. Actually, `idx_cards_supertype` in V1 is already a btree on `supertype` — skip creating it again. Updated V2:

```sql
-- Sprint 2 / BE-05: GIN indexes para búsqueda de cartas con pg_trgm
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_cards_name_trgm    ON cards USING gin(name gin_trgm_ops);
CREATE INDEX idx_cards_subtypes_gin ON cards USING gin(subtypes);
```

- [ ] **Step 2: Verify migration runs**

Start the backend once (requires running Postgres from `backend/src/main/resources/application-dev.yml`). Check Flyway output in logs for `V2__gin_indexes.sql` applied successfully. Skip this step if no local Postgres is available — CI will verify.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V2__gin_indexes.sql
git commit -m "feat(sprint-2): add GIN indexes for card name/subtype search"
```

---

## Task 3: JPA Entities — User, Deck, DeckCard

**Files:**
- Create: `backend/src/main/java/com/utn/pokemontcg/domain/model/User.java`
- Create: `backend/src/main/java/com/utn/pokemontcg/domain/model/DeckCardId.java`
- Create: `backend/src/main/java/com/utn/pokemontcg/domain/model/DeckCard.java`
- Create: `backend/src/main/java/com/utn/pokemontcg/domain/model/Deck.java`

Note: The V1 schema already has `users`, `decks`, and `deck_cards` tables fully defined. No new migrations needed.

- [ ] **Step 1: Create User.java**

```java
package com.utn.pokemontcg.domain.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected User() {}

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 2: Create DeckCardId.java**

```java
package com.utn.pokemontcg.domain.model;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class DeckCardId implements Serializable {

    private Long deckId;
    private String cardId;

    protected DeckCardId() {}

    public DeckCardId(Long deckId, String cardId) {
        this.deckId = deckId;
        this.cardId = cardId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeckCardId that)) return false;
        return Objects.equals(deckId, that.deckId) && Objects.equals(cardId, that.cardId);
    }

    @Override
    public int hashCode() { return Objects.hash(deckId, cardId); }
}
```

- [ ] **Step 3: Create DeckCard.java**

```java
package com.utn.pokemontcg.domain.model;

import jakarta.persistence.*;

@Entity
@Table(name = "deck_cards")
public class DeckCard {

    @EmbeddedId
    private DeckCardId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("deckId")
    @JoinColumn(name = "deck_id")
    private Deck deck;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("cardId")
    @JoinColumn(name = "card_id")
    private Card card;

    @Column(nullable = false)
    private int quantity;

    protected DeckCard() {}

    public DeckCard(Deck deck, Card card, int quantity) {
        this.id = new DeckCardId(deck.getId(), card.getId());
        this.deck = deck;
        this.card = card;
        this.quantity = quantity;
    }

    public DeckCardId getId() { return id; }
    public Deck getDeck() { return deck; }
    public Card getCard() { return card; }
    public int getQuantity() { return quantity; }

    public void setQuantity(int quantity) { this.quantity = quantity; }
}
```

- [ ] **Step 4: Create Deck.java**

```java
package com.utn.pokemontcg.domain.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "decks")
public class Deck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "is_valid", nullable = false)
    private boolean valid;

    @OneToMany(mappedBy = "deck", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    private List<DeckCard> cards = new ArrayList<>();

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected Deck() {}

    public Deck(User user, String name) {
        this.user = user;
        this.name = name;
        this.valid = false;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getName() { return name; }
    public boolean isValid() { return valid; }
    public List<DeckCard> getCards() { return cards; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setValid(boolean valid) { this.valid = valid; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 5: Compile to verify no errors**

```bash
cd backend && ./mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/utn/pokemontcg/domain/model/
git commit -m "feat(sprint-2): add User, Deck, DeckCard JPA entities"
```

---

## Task 4: Repositories — User, Deck

**Files:**
- Create: `backend/src/main/java/com/utn/pokemontcg/infrastructure/persistence/UserRepository.java`
- Create: `backend/src/main/java/com/utn/pokemontcg/infrastructure/persistence/DeckRepository.java`

- [ ] **Step 1: Create UserRepository.java**

```java
package com.utn.pokemontcg.infrastructure.persistence;

import com.utn.pokemontcg.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
```

- [ ] **Step 2: Create DeckRepository.java**

```java
package com.utn.pokemontcg.infrastructure.persistence;

import com.utn.pokemontcg.domain.model.Deck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeckRepository extends JpaRepository<Deck, Long> {

    List<Deck> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT d FROM Deck d JOIN FETCH d.cards dc JOIN FETCH dc.card WHERE d.id = :id")
    Optional<Deck> findByIdWithCards(@Param("id") Long id);
}
```

- [ ] **Step 3: Compile**

```bash
cd backend && ./mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/utn/pokemontcg/infrastructure/persistence/
git commit -m "feat(sprint-2): add UserRepository and DeckRepository"
```

---

## Task 5: JWT Infrastructure

**Files:**
- Create: `backend/src/main/java/com/utn/pokemontcg/config/JwtUtil.java`
- Create: `backend/src/main/java/com/utn/pokemontcg/security/JwtAuthFilter.java`
- Create: `backend/src/main/java/com/utn/pokemontcg/config/SecurityConfig.java`

- [ ] **Step 1: Create JwtUtil.java**

```java
package com.utn.pokemontcg.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

- [ ] **Step 2: Create JwtAuthFilter.java**

```java
package com.utn.pokemontcg.security;

import com.utn.pokemontcg.config.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.isValid(token)) {
                String username = jwtUtil.extractUsername(token);
                var auth = new UsernamePasswordAuthenticationToken(
                        username, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 3: Create SecurityConfig.java**

```java
package com.utn.pokemontcg.config;

import com.utn.pokemontcg.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/cards/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/actuator/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- [ ] **Step 4: Compile**

```bash
cd backend && ./mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/utn/pokemontcg/config/JwtUtil.java \
        backend/src/main/java/com/utn/pokemontcg/security/JwtAuthFilter.java \
        backend/src/main/java/com/utn/pokemontcg/config/SecurityConfig.java
git commit -m "feat(sprint-2): add JWT infrastructure and Spring Security config"
```

---

## Task 6: Auth Endpoints (Register + Login)

**Files:**
- Create: `backend/src/main/java/com/utn/pokemontcg/api/dto/AuthRequest.java`
- Create: `backend/src/main/java/com/utn/pokemontcg/api/dto/AuthResponse.java`
- Create: `backend/src/main/java/com/utn/pokemontcg/api/controller/AuthController.java`
- Create: `backend/src/test/java/com/utn/pokemontcg/api/controller/AuthControllerTest.java`

- [ ] **Step 1: Write failing AuthController test**

```java
package com.utn.pokemontcg.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utn.pokemontcg.api.dto.AuthRequest;
import com.utn.pokemontcg.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository userRepo;

    @BeforeEach
    void clean() { userRepo.deleteAll(); }

    @Test
    void register_returnsToken() throws Exception {
        var body = new AuthRequest("testuser", "test@example.com", "password123");
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void login_withValidCredentials_returnsToken() throws Exception {
        var reg = new AuthRequest("loginuser", "login@example.com", "secret");
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(reg))).andReturn();

        var login = new AuthRequest("loginuser", null, "secret");
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        var reg = new AuthRequest("badpassuser", "bad@example.com", "correct");
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(reg))).andReturn();

        var login = new AuthRequest("badpassuser", null, "wrong");
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_duplicateUsername_returns409() throws Exception {
        var body = new AuthRequest("dupuser", "dup@example.com", "pass");
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))).andReturn();

        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }
}
```

- [ ] **Step 2: Create AuthRequest.java**

```java
package com.utn.pokemontcg.api.dto;

public record AuthRequest(String username, String email, String password) {}
```

- [ ] **Step 3: Create AuthResponse.java**

```java
package com.utn.pokemontcg.api.dto;

public record AuthResponse(String token, String username) {}
```

- [ ] **Step 4: Create AuthController.java**

```java
package com.utn.pokemontcg.api.controller;

import com.utn.pokemontcg.api.dto.AuthRequest;
import com.utn.pokemontcg.api.dto.AuthResponse;
import com.utn.pokemontcg.config.JwtUtil;
import com.utn.pokemontcg.domain.model.User;
import com.utn.pokemontcg.infrastructure.persistence.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Registro e inicio de sesión")
public class AuthController {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepo, PasswordEncoder encoder, JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody AuthRequest req) {
        if (userRepo.existsByUsername(req.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username ya existe");
        }
        if (req.email() != null && userRepo.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email ya existe");
        }
        var user = new User(req.username(), req.email(), encoder.encode(req.password()));
        userRepo.save(user);
        return new AuthResponse(jwtUtil.generateToken(req.username()), req.username());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest req) {
        return userRepo.findByUsername(req.username())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .map(u -> ResponseEntity.ok(new AuthResponse(jwtUtil.generateToken(u.getUsername()), u.getUsername())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
```

- [ ] **Step 5: Run tests**

```bash
cd backend && ./mvnw test -Dtest=AuthControllerTest -q
```
Expected: All 4 tests PASS (requires running Postgres on `application-test.yml` settings, or skip if no Postgres — CI will run them)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/utn/pokemontcg/api/dto/AuthRequest.java \
        backend/src/main/java/com/utn/pokemontcg/api/dto/AuthResponse.java \
        backend/src/main/java/com/utn/pokemontcg/api/controller/AuthController.java \
        backend/src/test/java/com/utn/pokemontcg/api/controller/AuthControllerTest.java
git commit -m "feat(sprint-2): add register and login endpoints with JWT"
```

---

## Task 7: DeckValidator — Pure Java, No Spring

**Files:**
- Create: `backend/src/main/java/com/utn/pokemontcg/domain/service/DeckValidator.java`
- Create: `backend/src/test/java/com/utn/pokemontcg/domain/service/DeckValidatorTest.java`

The DeckValidator has **zero** Spring annotations. It is instantiated with `new DeckValidator()` in tests and in DeckService via `@Bean` or direct construction.

Rules (XY1 official):
1. Total card count (sum of quantities) must be exactly 60.
2. Max 4 copies of any single card **by name**, EXCEPT cards with `supertype == "Energy"` AND `subtypes.contains("Basic")` — those are unlimited.
3. Max 1 card with `subtypes.contains("ACE SPEC")`.
4. At least 1 card with `supertype == "Pokémon"` AND `subtypes.contains("Basic")`.

- [ ] **Step 1: Write DeckValidatorTest.java**

```java
package com.utn.pokemontcg.domain.service;

import com.utn.pokemontcg.domain.service.DeckValidator.CardEntry;
import com.utn.pokemontcg.domain.service.DeckValidator.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeckValidatorTest {

    private DeckValidator validator;

    @BeforeEach
    void setUp() { validator = new DeckValidator(); }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Build a valid 60-card deck: 4x Bulbasaur (Basic Pokémon) + 56x Grass Energy (Basic Energy) */
    private List<CardEntry> validBase() {
        var entries = new ArrayList<CardEntry>();
        entries.add(new CardEntry("Bulbasaur", "Pokémon", List.of("Basic"), 4));
        entries.add(new CardEntry("Grass Energy", "Energy", List.of("Basic"), 56));
        return entries;
    }

    // ── Total count ──────────────────────────────────────────────────────────

    @Test
    void valid_deck_passes() {
        var result = validator.validate(validBase());
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void deck_with_59_cards_fails() {
        var entries = new ArrayList<CardEntry>();
        entries.add(new CardEntry("Bulbasaur", "Pokémon", List.of("Basic"), 4));
        entries.add(new CardEntry("Grass Energy", "Energy", List.of("Basic"), 55));
        var result = validator.validate(entries);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("59") && e.contains("60"));
    }

    @Test
    void deck_with_61_cards_fails() {
        var entries = new ArrayList<CardEntry>();
        entries.add(new CardEntry("Bulbasaur", "Pokémon", List.of("Basic"), 5));
        entries.add(new CardEntry("Grass Energy", "Energy", List.of("Basic"), 56));
        var result = validator.validate(entries);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("61") && e.contains("60"));
    }

    // ── Max 4 copies ─────────────────────────────────────────────────────────

    @Test
    void five_copies_of_non_energy_fails() {
        var entries = new ArrayList<CardEntry>();
        entries.add(new CardEntry("Pikachu", "Pokémon", List.of("Basic"), 5));
        entries.add(new CardEntry("Grass Energy", "Energy", List.of("Basic"), 55));
        var result = validator.validate(entries);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("Pikachu") && e.contains("5"));
    }

    @Test
    void more_than_60_basic_energies_still_valid_if_total_is_60() {
        // Basic Energy is unlimited — 60 copies of same basic energy card is OK
        var entries = List.of(
            new CardEntry("Bulbasaur", "Pokémon", List.of("Basic"), 4),
            new CardEntry("Grass Energy", "Energy", List.of("Basic"), 56)
        );
        var result = validator.validate(entries);
        assertThat(result.valid()).isTrue();
    }

    @Test
    void non_basic_energy_is_limited_to_4() {
        var entries = new ArrayList<CardEntry>();
        entries.add(new CardEntry("Double Colorless Energy", "Energy", List.of("Special"), 5));
        entries.add(new CardEntry("Bulbasaur", "Pokémon", List.of("Basic"), 4));
        entries.add(new CardEntry("Grass Energy", "Energy", List.of("Basic"), 51));
        var result = validator.validate(entries);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("Double Colorless Energy"));
    }

    // ── ACE SPEC (AS TÁCTICO) ─────────────────────────────────────────────

    @Test
    void two_ace_spec_cards_fails() {
        var entries = new ArrayList<CardEntry>();
        entries.add(new CardEntry("Computer Search", "Trainer", List.of("Item", "ACE SPEC"), 1));
        entries.add(new CardEntry("Dowsing Machine", "Trainer", List.of("Item", "ACE SPEC"), 1));
        entries.add(new CardEntry("Bulbasaur", "Pokémon", List.of("Basic"), 4));
        entries.add(new CardEntry("Grass Energy", "Energy", List.of("Basic"), 54));
        var result = validator.validate(entries);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("AS TÁCTICO"));
    }

    @Test
    void one_ace_spec_card_is_allowed() {
        var entries = new ArrayList<CardEntry>();
        entries.add(new CardEntry("Computer Search", "Trainer", List.of("Item", "ACE SPEC"), 1));
        entries.add(new CardEntry("Bulbasaur", "Pokémon", List.of("Basic"), 4));
        entries.add(new CardEntry("Grass Energy", "Energy", List.of("Basic"), 55));
        var result = validator.validate(entries);
        assertThat(result.valid()).isTrue();
    }

    // ── At least 1 Basic Pokémon ─────────────────────────────────────────────

    @Test
    void deck_without_basic_pokemon_fails() {
        var entries = new ArrayList<CardEntry>();
        entries.add(new CardEntry("Ivysaur", "Pokémon", List.of("Stage 1"), 4));
        entries.add(new CardEntry("Grass Energy", "Energy", List.of("Basic"), 56));
        var result = validator.validate(entries);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("Pokémon Básico"));
    }

    @Test
    void empty_deck_fails_with_multiple_errors() {
        var result = validator.validate(List.of());
        assertThat(result.valid()).isFalse();
        assertThat(result.errors().size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void error_messages_are_in_spanish() {
        var entries = List.of(new CardEntry("Grass Energy", "Energy", List.of("Basic"), 60));
        var result = validator.validate(entries);
        // No basic Pokémon — should produce Spanish error
        assertThat(result.errors()).anyMatch(e ->
            e.contains("Básico") || e.contains("básico") || e.contains("mazo"));
    }
}
```

- [ ] **Step 2: Run test to confirm it fails (class does not exist yet)**

```bash
cd backend && ./mvnw test -Dtest=DeckValidatorTest -q 2>&1 | head -20
```
Expected: FAIL — "cannot find symbol: class DeckValidator"

- [ ] **Step 3: Create DeckValidator.java**

```java
package com.utn.pokemontcg.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DeckValidator {

    public record CardEntry(String name, String supertype, List<String> subtypes, int quantity) {}

    public record ValidationResult(boolean valid, List<String> errors) {}

    public ValidationResult validate(List<CardEntry> entries) {
        var errors = new ArrayList<String>();

        int total = entries.stream().mapToInt(CardEntry::quantity).sum();
        if (total != 60) {
            errors.add("El mazo debe tener exactamente 60 cartas. Cantidad actual: " + total + ".");
        }

        // Group by name, check max 4 (skip Basic Energies)
        Map<String, Integer> countByName = entries.stream()
                .collect(Collectors.groupingBy(CardEntry::name,
                        Collectors.summingInt(CardEntry::quantity)));

        for (var entry : entries) {
            if (isBasicEnergy(entry)) continue;
            int count = countByName.getOrDefault(entry.name(), 0);
            if (count > 4) {
                errors.add("La carta '" + entry.name() + "' aparece " + count
                        + " veces (máximo 4).");
            }
        }

        // Max 1 ACE SPEC
        long aceSpecCount = entries.stream()
                .filter(e -> e.subtypes().contains("ACE SPEC"))
                .mapToLong(CardEntry::quantity)
                .sum();
        if (aceSpecCount > 1) {
            String aceNames = entries.stream()
                    .filter(e -> e.subtypes().contains("ACE SPEC"))
                    .map(CardEntry::name)
                    .collect(Collectors.joining(", "));
            errors.add("Solo se permite 1 carta AS TÁCTICO. Encontradas: " + aceNames + ".");
        }

        // At least 1 Basic Pokémon
        boolean hasBasicPokemon = entries.stream().anyMatch(this::isBasicPokemon);
        if (!hasBasicPokemon) {
            errors.add("El mazo debe incluir al menos 1 Pokémon Básico.");
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    private boolean isBasicEnergy(CardEntry e) {
        return "Energy".equals(e.supertype()) && e.subtypes().contains("Basic");
    }

    private boolean isBasicPokemon(CardEntry e) {
        return "Pokémon".equals(e.supertype()) && e.subtypes().contains("Basic");
    }
}
```

- [ ] **Step 4: Run tests**

```bash
cd backend && ./mvnw test -Dtest=DeckValidatorTest -q
```
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/utn/pokemontcg/domain/service/DeckValidator.java \
        backend/src/test/java/com/utn/pokemontcg/domain/service/DeckValidatorTest.java
git commit -m "feat(sprint-2): add DeckValidator with XY1 rules and full unit tests"
```

---

## Task 8: DeckService + DTOs + DeckController

**Files:**
- Create: `backend/src/main/java/com/utn/pokemontcg/api/dto/DeckCardDto.java`
- Create: `backend/src/main/java/com/utn/pokemontcg/api/dto/DeckDto.java`
- Create: `backend/src/main/java/com/utn/pokemontcg/api/dto/ValidationResultDto.java`
- Create: `backend/src/main/java/com/utn/pokemontcg/application/service/DeckService.java`
- Create: `backend/src/main/java/com/utn/pokemontcg/api/controller/DeckController.java`
- Create: `backend/src/test/java/com/utn/pokemontcg/application/service/DeckServiceTest.java`

- [ ] **Step 1: Create DTOs**

```java
// DeckCardDto.java
package com.utn.pokemontcg.api.dto;

public record DeckCardDto(String cardId, String cardName, String imageSmall, int quantity) {}
```

```java
// DeckDto.java
package com.utn.pokemontcg.api.dto;

import java.util.List;

public record DeckDto(Long id, String name, boolean valid, List<DeckCardDto> cards,
                      String createdAt, String updatedAt) {}
```

```java
// ValidationResultDto.java
package com.utn.pokemontcg.api.dto;

import java.util.List;

public record ValidationResultDto(boolean valid, List<String> errors) {}
```

- [ ] **Step 2: Create DeckService.java**

```java
package com.utn.pokemontcg.application.service;

import com.utn.pokemontcg.api.dto.DeckCardDto;
import com.utn.pokemontcg.api.dto.DeckDto;
import com.utn.pokemontcg.api.dto.ValidationResultDto;
import com.utn.pokemontcg.domain.model.Deck;
import com.utn.pokemontcg.domain.model.DeckCard;
import com.utn.pokemontcg.domain.service.DeckValidator;
import com.utn.pokemontcg.infrastructure.persistence.CardRepository;
import com.utn.pokemontcg.infrastructure.persistence.DeckRepository;
import com.utn.pokemontcg.infrastructure.persistence.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class DeckService {

    private final DeckRepository deckRepo;
    private final UserRepository userRepo;
    private final CardRepository cardRepo;
    private final DeckValidator validator = new DeckValidator();

    public DeckService(DeckRepository deckRepo, UserRepository userRepo,
                       CardRepository cardRepo) {
        this.deckRepo = deckRepo;
        this.userRepo = userRepo;
        this.cardRepo = cardRepo;
    }

    public List<DeckDto> listForUser(String username) {
        var user = requireUser(username);
        return deckRepo.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public DeckDto create(String username, String name) {
        var user = requireUser(username);
        var deck = deckRepo.save(new Deck(user, name));
        return toDto(deck);
    }

    @Transactional
    public DeckDto updateCards(String username, Long deckId,
                               Map<String, Integer> cardQuantities) {
        var deck = requireOwned(username, deckId);
        deck.getCards().clear();

        for (var entry : cardQuantities.entrySet()) {
            var card = cardRepo.findById(entry.getKey())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Carta no encontrada: " + entry.getKey()));
            deck.getCards().add(new DeckCard(deck, card, entry.getValue()));
        }

        var entries = deck.getCards().stream()
                .map(dc -> new DeckValidator.CardEntry(
                        dc.getCard().getName(),
                        dc.getCard().getSupertype(),
                        dc.getCard().getSubtypes(),
                        dc.getQuantity()))
                .toList();

        deck.setValid(validator.validate(entries).valid());
        deck.setUpdatedAt(OffsetDateTime.now());
        return toDto(deckRepo.save(deck));
    }

    public ValidationResultDto validate(String username, Long deckId) {
        var deck = requireOwned(username, deckId);
        var entries = loadEntries(deck);
        var result = validator.validate(entries);
        return new ValidationResultDto(result.valid(), result.errors());
    }

    @Transactional
    public void delete(String username, Long deckId) {
        requireOwned(username, deckId);
        deckRepo.deleteById(deckId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private com.utn.pokemontcg.domain.model.User requireUser(String username) {
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuario no encontrado: " + username));
    }

    private Deck requireOwned(String username, Long deckId) {
        var user = requireUser(username);
        var deck = deckRepo.findByIdWithCards(deckId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Mazo no encontrado"));
        if (!deck.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        return deck;
    }

    private List<DeckValidator.CardEntry> loadEntries(Deck deck) {
        return deck.getCards().stream()
                .map(dc -> new DeckValidator.CardEntry(
                        dc.getCard().getName(),
                        dc.getCard().getSupertype(),
                        dc.getCard().getSubtypes(),
                        dc.getQuantity()))
                .toList();
    }

    private DeckDto toDto(Deck d) {
        var cards = d.getCards().stream()
                .map(dc -> new DeckCardDto(
                        dc.getCard().getId(),
                        dc.getCard().getName(),
                        dc.getCard().getImageSmall(),
                        dc.getQuantity()))
                .toList();
        return new DeckDto(
                d.getId(), d.getName(), d.isValid(), cards,
                d.getCreatedAt() != null ? d.getCreatedAt().toString() : null,
                d.getUpdatedAt() != null ? d.getUpdatedAt().toString() : null);
    }
}
```

- [ ] **Step 3: Write DeckServiceTest.java**

```java
package com.utn.pokemontcg.application.service;

import com.utn.pokemontcg.domain.model.Deck;
import com.utn.pokemontcg.domain.model.User;
import com.utn.pokemontcg.infrastructure.persistence.CardRepository;
import com.utn.pokemontcg.infrastructure.persistence.DeckRepository;
import com.utn.pokemontcg.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeckServiceTest {

    @Mock DeckRepository deckRepo;
    @Mock UserRepository userRepo;
    @Mock CardRepository cardRepo;

    DeckService service;

    User user = new User("alice", "alice@test.com", "hash");
    User other = new User("bob", "bob@test.com", "hash");

    @BeforeEach
    void setUp() {
        service = new DeckService(deckRepo, userRepo, cardRepo);
        // Reflectively set IDs for test users
        setId(user, 1L);
        setId(other, 2L);
    }

    @Test
    void create_savesNewDeck() {
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        var saved = new Deck(user, "My Deck");
        setId(saved, 10L);
        when(deckRepo.save(any(Deck.class))).thenReturn(saved);

        var dto = service.create("alice", "My Deck");

        assertThat(dto.name()).isEqualTo("My Deck");
        verify(deckRepo).save(any(Deck.class));
    }

    @Test
    void validate_throwsForbidden_whenNotOwner() {
        when(userRepo.findByUsername("bob")).thenReturn(Optional.of(other));
        var deck = new Deck(user, "Alice's Deck");
        setId(deck, 5L);
        when(deckRepo.findByIdWithCards(5L)).thenReturn(Optional.of(deck));

        assertThatThrownBy(() -> service.validate("bob", 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void listForUser_returnsOnlyUserDecks() {
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        when(deckRepo.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        var result = service.listForUser("alice");

        assertThat(result).isEmpty();
    }

    // Reflection helper to set private ID field for test entities
    private void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
```

- [ ] **Step 4: Run DeckService tests**

```bash
cd backend && ./mvnw test -Dtest=DeckServiceTest -q
```
Expected: All 3 tests PASS (pure Mockito, no DB needed)

- [ ] **Step 5: Create DeckController.java**

```java
package com.utn.pokemontcg.api.controller;

import com.utn.pokemontcg.api.dto.DeckDto;
import com.utn.pokemontcg.api.dto.ValidationResultDto;
import com.utn.pokemontcg.application.service.DeckService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/decks")
@Tag(name = "Decks", description = "CRUD de mazos y validación")
public class DeckController {

    private final DeckService deckService;

    public DeckController(DeckService deckService) {
        this.deckService = deckService;
    }

    @GetMapping
    public List<DeckDto> list(@AuthenticationPrincipal String username) {
        return deckService.listForUser(username);
    }

    @PostMapping
    public DeckDto create(@AuthenticationPrincipal String username,
                          @RequestParam String name) {
        return deckService.create(username, name);
    }

    @PutMapping("/{id}/cards")
    public DeckDto updateCards(@AuthenticationPrincipal String username,
                               @PathVariable Long id,
                               @RequestBody Map<String, Integer> cardQuantities) {
        return deckService.updateCards(username, id, cardQuantities);
    }

    @PostMapping("/{id}/validate")
    public ValidationResultDto validate(@AuthenticationPrincipal String username,
                                        @PathVariable Long id) {
        return deckService.validate(username, id);
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal String username,
                       @PathVariable Long id) {
        deckService.delete(username, id);
    }
}
```

- [ ] **Step 6: Compile**

```bash
cd backend && ./mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/utn/pokemontcg/api/dto/ \
        backend/src/main/java/com/utn/pokemontcg/application/service/DeckService.java \
        backend/src/main/java/com/utn/pokemontcg/api/controller/DeckController.java \
        backend/src/test/java/com/utn/pokemontcg/application/service/DeckServiceTest.java
git commit -m "feat(sprint-2): add DeckService, DeckController, and deck DTOs"
```

---

## Task 9: Card Search Endpoint

**Files:**
- Modify: `backend/src/main/java/com/utn/pokemontcg/infrastructure/persistence/CardRepository.java`
- Modify: `backend/src/main/java/com/utn/pokemontcg/api/controller/CardController.java`

- [ ] **Step 1: Add native search query to CardRepository.java**

Add to the existing `CardRepository` interface:

```java
@Query(value = """
        SELECT * FROM cards
        WHERE (:name IS NULL OR name ILIKE '%' || :name || '%')
          AND (:supertype IS NULL OR supertype = :supertype)
          AND (:subtype IS NULL OR subtypes @> CAST(:subtype AS jsonb))
        ORDER BY CAST(number AS integer) ASC
        """, nativeQuery = true)
List<Card> search(@Param("name") String name,
                  @Param("supertype") String supertype,
                  @Param("subtype") String subtype);
```

New imports needed in CardRepository:
- `import org.springframework.data.jpa.repository.Query;` (already present)
- `import org.springframework.data.repository.query.Param;` (already present)

- [ ] **Step 2: Add search endpoint to CardController.java**

Add to the existing `CardController` class (keep existing `list` method):

```java
@GetMapping("/search")
@Operation(summary = "Busca cartas por nombre, supertipo y/o subtipo")
public List<CardDto> search(
        @RequestParam(required = false) String name,
        @RequestParam(required = false) String supertype,
        @RequestParam(required = false) String subtype) {
    // Wrap subtype in JSON array for JSONB @> containment operator
    String subtypeJson = subtype != null ? "[\"" + subtype + "\"]" : null;
    return service.search(name, supertype, subtypeJson).stream()
            .map(CardDto::from).toList();
}
```

Also inject `CardRepository` into `CardController` via `CardCatalogService`, OR directly:

Change the controller to also accept `CardRepository` — but prefer keeping it via the service layer. Add `search` to `CardCatalogService`:

In `CardCatalogService.java`, add:

```java
public List<Card> search(String name, String supertype, String subtypeJson) {
    return cardRepo.search(name, supertype, subtypeJson);
}
```

Where `cardRepo` is the existing `CardRepository` already injected in `CardCatalogService`.

- [ ] **Step 3: Compile**

```bash
cd backend && ./mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Verify CardCatalogService has the cardRepo field**

Read `backend/src/main/java/com/utn/pokemontcg/application/service/CardCatalogService.java` to confirm it has `CardRepository` injected. If it does not, inject it:

```java
private final CardRepository cardRepo;

public CardCatalogService(CardRepository cardRepo, ...) {
    this.cardRepo = cardRepo;
    ...
}
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/utn/pokemontcg/infrastructure/persistence/CardRepository.java \
        backend/src/main/java/com/utn/pokemontcg/application/service/CardCatalogService.java \
        backend/src/main/java/com/utn/pokemontcg/api/controller/CardController.java
git commit -m "feat(sprint-2): add card search endpoint with pg_trgm ILIKE and JSONB subtype filter"
```

---

## Task 10: Frontend — Install CDK + Auth Models + Services

**Files:**
- Modify: `frontend/package.json`
- Create: `frontend/src/app/core/models/user.model.ts`
- Create: `frontend/src/app/core/services/auth.service.ts`
- Create: `frontend/src/app/core/services/deck.service.ts`
- Create: `frontend/src/app/core/interceptors/auth.interceptor.ts`
- Create: `frontend/src/app/core/guards/auth.guard.ts`
- Modify: `frontend/src/app/core/services/card.service.ts`
- Modify: `frontend/src/app/app.config.ts`
- Modify: `frontend/src/app/app.routes.ts`

- [ ] **Step 1: Install Angular CDK**

```bash
cd frontend && npm install @angular/cdk@^21
```
Expected: package added to dependencies.

- [ ] **Step 2: Create user.model.ts**

```typescript
export interface AuthRequest {
  username: string;
  email?: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  username: string;
}
```

- [ ] **Step 3: Create auth.service.ts**

```typescript
import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthRequest, AuthResponse } from '../models/user.model';

const TOKEN_KEY = 'ptcg_token';
const USERNAME_KEY = 'ptcg_username';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private readonly base = `${environment.apiBaseUrl}/auth`;

  readonly currentUser = signal<string | null>(this.storedUsername());

  login(req: AuthRequest) {
    return this.http.post<AuthResponse>(`${this.base}/login`, req).pipe(
      tap(res => this.storeSession(res))
    );
  }

  register(req: AuthRequest) {
    return this.http.post<AuthResponse>(`${this.base}/register`, req).pipe(
      tap(res => this.storeSession(res))
    );
  }

  logout() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USERNAME_KEY);
    this.currentUser.set(null);
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  isLoggedIn(): boolean {
    return this.getToken() !== null;
  }

  private storeSession(res: AuthResponse) {
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(USERNAME_KEY, res.username);
    this.currentUser.set(res.username);
  }

  private storedUsername(): string | null {
    return localStorage.getItem(USERNAME_KEY);
  }
}
```

- [ ] **Step 4: Create auth.interceptor.ts**

```typescript
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).getToken();
  if (token) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
};
```

- [ ] **Step 5: Create auth.guard.ts**

```typescript
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isLoggedIn()) return true;
  return router.createUrlTree(['/login']);
};
```

- [ ] **Step 6: Add search() to card.service.ts**

Append to `CardService`:

```typescript
search(params: { name?: string; supertype?: string; subtype?: string }): Observable<Card[]> {
  return this.http.get<Card[]>(`${this.base}/search`, { params: params as Record<string, string> });
}
```

- [ ] **Step 7: Create deck.service.ts**

```typescript
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Deck } from '../models/deck.model';

export interface ValidationResult {
  valid: boolean;
  errors: string[];
}

@Injectable({ providedIn: 'root' })
export class DeckService {
  private http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/decks`;

  list(): Observable<Deck[]> {
    return this.http.get<Deck[]>(this.base);
  }

  create(name: string): Observable<Deck> {
    return this.http.post<Deck>(this.base, null, { params: { name } });
  }

  updateCards(deckId: number, cards: Record<string, number>): Observable<Deck> {
    return this.http.put<Deck>(`${this.base}/${deckId}/cards`, cards);
  }

  validate(deckId: number): Observable<ValidationResult> {
    return this.http.post<ValidationResult>(`${this.base}/${deckId}/validate`, null);
  }

  delete(deckId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${deckId}`);
  }
}
```

- [ ] **Step 8: Update app.config.ts**

Replace content:

```typescript
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { authInterceptor } from './core/interceptors/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
  ],
};
```

- [ ] **Step 9: Compile TypeScript**

```bash
cd frontend && npm run build -- --configuration=development 2>&1 | tail -5
```
Expected: Build successful

- [ ] **Step 10: Commit**

```bash
git add frontend/package.json frontend/package-lock.json \
        frontend/src/app/core/
git commit -m "feat(sprint-2): add auth service, deck service, interceptor, guard, and CDK"
```

---

## Task 11: Auth Pages (Login + Register)

**Files:**
- Create: `frontend/src/app/features/auth/login/login.component.ts`
- Create: `frontend/src/app/features/auth/login/login.component.html`
- Create: `frontend/src/app/features/auth/register/register.component.ts`
- Create: `frontend/src/app/features/auth/register/register.component.html`
- Modify: `frontend/src/app/app.routes.ts`

- [ ] **Step 1: Create login.component.ts**

```typescript
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, CommonModule, RouterLink],
  templateUrl: './login.component.html',
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  form = this.fb.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  error = '';

  submit() {
    if (this.form.invalid) return;
    const { username, password } = this.form.value;
    this.auth.login({ username: username!, password: password! }).subscribe({
      next: () => this.router.navigate(['/deck-builder']),
      error: () => (this.error = 'Usuario o contraseña incorrectos'),
    });
  }
}
```

- [ ] **Step 2: Create login.component.html**

```html
<div class="auth-container">
  <h1>Iniciar Sesión</h1>
  <form [formGroup]="form" (ngSubmit)="submit()">
    <div class="field">
      <label>Usuario</label>
      <input formControlName="username" type="text" placeholder="tu_usuario" />
    </div>
    <div class="field">
      <label>Contraseña</label>
      <input formControlName="password" type="password" placeholder="••••••••" />
    </div>
    @if (error) {
      <p class="error">{{ error }}</p>
    }
    <button type="submit" [disabled]="form.invalid">Entrar</button>
  </form>
  <p>¿No tenés cuenta? <a routerLink="/register">Registrate</a></p>
</div>
```

- [ ] **Step 3: Create register.component.ts**

```typescript
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, CommonModule, RouterLink],
  templateUrl: './register.component.html',
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  form = this.fb.group({
    username: ['', [Validators.required, Validators.minLength(3)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  error = '';

  submit() {
    if (this.form.invalid) return;
    const { username, email, password } = this.form.value;
    this.auth.register({ username: username!, email: email!, password: password! }).subscribe({
      next: () => this.router.navigate(['/deck-builder']),
      error: (e) => (this.error = e.status === 409
        ? 'El usuario o email ya existe'
        : 'Error al registrarse'),
    });
  }
}
```

- [ ] **Step 4: Create register.component.html**

```html
<div class="auth-container">
  <h1>Crear Cuenta</h1>
  <form [formGroup]="form" (ngSubmit)="submit()">
    <div class="field">
      <label>Usuario</label>
      <input formControlName="username" type="text" placeholder="tu_usuario" />
    </div>
    <div class="field">
      <label>Email</label>
      <input formControlName="email" type="email" placeholder="tu@email.com" />
    </div>
    <div class="field">
      <label>Contraseña</label>
      <input formControlName="password" type="password" placeholder="mínimo 6 caracteres" />
    </div>
    @if (error) {
      <p class="error">{{ error }}</p>
    }
    <button type="submit" [disabled]="form.invalid">Registrarse</button>
  </form>
  <p>¿Ya tenés cuenta? <a routerLink="/login">Iniciá sesión</a></p>
</div>
```

- [ ] **Step 5: Update app.routes.ts**

```typescript
import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register.component').then(m => m.RegisterComponent),
  },
  {
    path: 'deck-builder',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/deck-builder/deck-builder.component').then(m => m.DeckBuilderComponent),
  },
  { path: '', redirectTo: '/deck-builder', pathMatch: 'full' },
  { path: '**', redirectTo: '/login' },
];
```

- [ ] **Step 6: Build**

```bash
cd frontend && npm run build -- --configuration=development 2>&1 | tail -5
```
Expected: Build successful

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/features/auth/ frontend/src/app/app.routes.ts
git commit -m "feat(sprint-2): add login and register pages with reactive forms"
```

---

## Task 12: CardPreviewComponent + DeckBuilderComponent

**Files:**
- Create: `frontend/src/app/shared/components/card-preview/card-preview.component.ts`
- Create: `frontend/src/app/shared/components/card-preview/card-preview.component.html`
- Create: `frontend/src/app/shared/components/card-preview/card-preview.component.scss`
- Create: `frontend/src/app/features/deck-builder/deck-editor/deck-editor.component.ts`
- Create: `frontend/src/app/features/deck-builder/deck-editor/deck-editor.component.html`
- Create: `frontend/src/app/features/deck-builder/deck-builder.component.ts`
- Create: `frontend/src/app/features/deck-builder/deck-builder.component.html`
- Create: `frontend/src/app/features/deck-builder/deck-builder.component.scss`

- [ ] **Step 1: Create card-preview.component.ts**

Designed with `draggable` and `showAddButton` flags so Sprint 5 match board reuses it without changes.

```typescript
import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Card } from '../../../core/models/card.model';

@Component({
  selector: 'app-card-preview',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './card-preview.component.html',
  styleUrl: './card-preview.component.scss',
})
export class CardPreviewComponent {
  @Input() card!: Card;
  @Input() quantity = 0;
  @Input() draggable = false;
  @Input() showAddButton = false;

  @Output() addCard = new EventEmitter<Card>();
  @Output() removeCard = new EventEmitter<Card>();

  onAdd() { this.addCard.emit(this.card); }
  onRemove() { this.removeCard.emit(this.card); }
}
```

- [ ] **Step 2: Create card-preview.component.html**

```html
<div class="card-preview" [attr.draggable]="draggable || null">
  @if (card.imageSmall) {
    <img [src]="card.imageSmall" [alt]="card.name" class="card-image" />
  } @else {
    <div class="card-placeholder">{{ card.name }}</div>
  }
  <div class="card-info">
    <span class="card-name">{{ card.name }}</span>
    @if (quantity > 0) {
      <span class="card-qty">×{{ quantity }}</span>
    }
  </div>
  @if (showAddButton) {
    <div class="card-actions">
      <button (click)="onAdd()" class="btn-add" title="Agregar al mazo">+</button>
      @if (quantity > 0) {
        <button (click)="onRemove()" class="btn-remove" title="Quitar del mazo">−</button>
      }
    </div>
  }
</div>
```

- [ ] **Step 3: Create card-preview.component.scss**

```scss
.card-preview {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 120px;
  cursor: default;

  &[draggable] { cursor: grab; }

  .card-image { width: 100%; border-radius: 4px; }
  .card-placeholder {
    width: 100%; height: 160px; background: #2a2a4a;
    display: flex; align-items: center; justify-content: center;
    border-radius: 4px; font-size: 0.75rem; text-align: center; padding: 4px;
  }
  .card-info {
    display: flex; justify-content: space-between; width: 100%;
    font-size: 0.7rem; margin-top: 4px;
    .card-qty { font-weight: bold; color: #f6c90e; }
  }
  .card-actions {
    display: flex; gap: 4px; margin-top: 4px;
    button { width: 28px; height: 28px; border-radius: 50%; border: none;
             cursor: pointer; font-size: 1rem; font-weight: bold; }
    .btn-add { background: #4caf50; color: white; }
    .btn-remove { background: #f44336; color: white; }
  }
}
```

- [ ] **Step 4: Create deck-editor.component.ts**

```typescript
import { Component, Input, Output, EventEmitter, OnChanges, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject } from 'rxjs';
import { debounceTime, switchMap } from 'rxjs/operators';
import { Card } from '../../../core/models/card.model';
import { DeckService, ValidationResult } from '../../../core/services/deck.service';
import { CardPreviewComponent } from '../../../shared/components/card-preview/card-preview.component';

export interface DeckState {
  deckId: number;
  cards: Record<string, number>;
}

@Component({
  selector: 'app-deck-editor',
  standalone: true,
  imports: [CommonModule, CardPreviewComponent],
  templateUrl: './deck-editor.component.html',
})
export class DeckEditorComponent implements OnChanges {
  @Input() deckState!: DeckState;
  @Input() allCards: Card[] = [];
  @Output() deckUpdated = new EventEmitter<Record<string, number>>();

  private deckService = inject(DeckService);
  private validateTrigger = new Subject<void>();

  validation: ValidationResult | null = null;
  saving = false;

  get totalCards(): number {
    return Object.values(this.deckState?.cards ?? {}).reduce((a, b) => a + b, 0);
  }

  get cardEntries(): Array<{ card: Card; qty: number }> {
    return Object.entries(this.deckState?.cards ?? {})
      .map(([cardId, qty]) => ({
        card: this.allCards.find(c => c.id === cardId)!,
        qty,
      }))
      .filter(e => e.card);
  }

  ngOnChanges() {
    this.validateTrigger.pipe(
      debounceTime(300),
      switchMap(() => this.deckService.validate(this.deckState.deckId))
    ).subscribe(r => (this.validation = r));
  }

  addCard(card: Card) {
    const current = this.deckState.cards[card.id] ?? 0;
    this.deckState = {
      ...this.deckState,
      cards: { ...this.deckState.cards, [card.id]: current + 1 },
    };
    this.save();
  }

  removeCard(card: Card) {
    const current = this.deckState.cards[card.id] ?? 0;
    if (current <= 1) {
      const { [card.id]: _, ...rest } = this.deckState.cards;
      this.deckState = { ...this.deckState, cards: rest };
    } else {
      this.deckState = {
        ...this.deckState,
        cards: { ...this.deckState.cards, [card.id]: current - 1 },
      };
    }
    this.save();
  }

  private save() {
    this.saving = true;
    this.deckService.updateCards(this.deckState.deckId, this.deckState.cards).subscribe({
      next: () => {
        this.saving = false;
        this.deckUpdated.emit(this.deckState.cards);
        this.validateTrigger.next();
      },
      error: () => (this.saving = false),
    });
  }
}
```

- [ ] **Step 5: Create deck-editor.component.html**

```html
<div class="deck-editor">
  <div class="editor-header">
    <h2>Mazo ({{ totalCards }}/60)</h2>
    @if (saving) { <span class="saving">Guardando…</span> }
    @if (validation) {
      <span class="validation-badge" [class.valid]="validation.valid"
            [class.invalid]="!validation.valid">
        {{ validation.valid ? '✓ Válido' : '✗ Inválido' }}
      </span>
    }
  </div>

  @if (validation && !validation.valid) {
    <ul class="error-list">
      @for (err of validation.errors; track err) {
        <li>{{ err }}</li>
      }
    </ul>
  }

  <div class="card-grid">
    @for (entry of cardEntries; track entry.card.id) {
      <app-card-preview
        [card]="entry.card"
        [quantity]="entry.qty"
        [showAddButton]="true"
        (addCard)="addCard($event)"
        (removeCard)="removeCard($event)" />
    }
  </div>
</div>
```

- [ ] **Step 6: Create deck-builder.component.ts**

```typescript
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { CardService } from '../../core/services/card.service';
import { DeckService } from '../../core/services/deck.service';
import { AuthService } from '../../core/services/auth.service';
import { Card } from '../../core/models/card.model';
import { Deck } from '../../core/models/deck.model';
import { CardPreviewComponent } from '../../shared/components/card-preview/card-preview.component';
import { DeckEditorComponent, DeckState } from './deck-editor/deck-editor.component';

@Component({
  selector: 'app-deck-builder',
  standalone: true,
  imports: [CommonModule, FormsModule, CardPreviewComponent, DeckEditorComponent],
  templateUrl: './deck-builder.component.html',
  styleUrl: './deck-builder.component.scss',
})
export class DeckBuilderComponent implements OnInit {
  private cardService = inject(CardService);
  private deckService = inject(DeckService);
  private authService = inject(AuthService);

  // Catalog state
  cards: Card[] = [];
  searchName = '';
  filterSupertype = '';
  filterSubtype = '';
  private searchTrigger = new Subject<void>();

  // Deck state
  decks: Deck[] = [];
  activeDeck: DeckState | null = null;
  newDeckName = '';

  ngOnInit() {
    this.loadCards();
    this.loadDecks();

    this.searchTrigger.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(() => this.cardService.search({
        name: this.searchName || undefined,
        supertype: this.filterSupertype || undefined,
        subtype: this.filterSubtype || undefined,
      }))
    ).subscribe(cards => (this.cards = cards));
  }

  private loadCards() {
    this.cardService.search({}).subscribe(cards => (this.cards = cards));
  }

  private loadDecks() {
    this.deckService.list().subscribe(decks => {
      this.decks = decks;
      if (decks.length && !this.activeDeck) this.selectDeck(decks[0]);
    });
  }

  onSearchChange() { this.searchTrigger.next(); }

  selectDeck(deck: Deck) {
    const cards: Record<string, number> = {};
    for (const dc of deck.cards) cards[dc.cardId] = dc.quantity;
    this.activeDeck = { deckId: deck.id, cards };
  }

  createDeck() {
    if (!this.newDeckName.trim()) return;
    this.deckService.create(this.newDeckName.trim()).subscribe(deck => {
      this.decks.unshift(deck);
      this.selectDeck(deck);
      this.newDeckName = '';
    });
  }

  addCardToDeck(card: Card) {
    if (!this.activeDeck) return;
    const current = this.activeDeck.cards[card.id] ?? 0;
    this.activeDeck = {
      ...this.activeDeck,
      cards: { ...this.activeDeck.cards, [card.id]: current + 1 },
    };
    this.deckService.updateCards(this.activeDeck.deckId, this.activeDeck.cards).subscribe();
  }

  logout() { this.authService.logout(); }
}
```

- [ ] **Step 7: Create deck-builder.component.html**

```html
<div class="deck-builder-page">
  <header class="page-header">
    <h1>Constructor de Mazos</h1>
    <button (click)="logout()" class="btn-logout">Cerrar sesión</button>
  </header>

  <div class="builder-layout">
    <!-- LEFT: Card Catalog -->
    <aside class="catalog-panel">
      <div class="search-controls">
        <input [(ngModel)]="searchName" (ngModelChange)="onSearchChange()"
               placeholder="Buscar por nombre..." class="search-input" />
        <select [(ngModel)]="filterSupertype" (ngModelChange)="onSearchChange()">
          <option value="">Todos los tipos</option>
          <option value="Pokémon">Pokémon</option>
          <option value="Trainer">Entrenador</option>
          <option value="Energy">Energía</option>
        </select>
      </div>

      <div class="catalog-grid">
        @for (card of cards; track card.id) {
          <app-card-preview
            [card]="card"
            [showAddButton]="!!activeDeck"
            (addCard)="addCardToDeck($event)" />
        }
      </div>
    </aside>

    <!-- RIGHT: Deck Editor -->
    <main class="editor-panel">
      <div class="deck-list-header">
        <h2>Mis Mazos</h2>
        <div class="new-deck-form">
          <input [(ngModel)]="newDeckName" placeholder="Nombre del mazo..." />
          <button (click)="createDeck()" [disabled]="!newDeckName.trim()">+ Crear</button>
        </div>
      </div>

      <div class="deck-tabs">
        @for (deck of decks; track deck.id) {
          <button (click)="selectDeck(deck)"
                  [class.active]="activeDeck?.deckId === deck.id">
            {{ deck.name }}
            <span [class.valid-dot]="deck.isValid" [class.invalid-dot]="!deck.isValid">●</span>
          </button>
        }
      </div>

      @if (activeDeck) {
        <app-deck-editor
          [deckState]="activeDeck"
          [allCards]="cards" />
      } @else {
        <div class="no-deck">Creá o seleccioná un mazo para empezar</div>
      }
    </main>
  </div>
</div>
```

- [ ] **Step 8: Create deck-builder.component.scss**

```scss
.deck-builder-page {
  display: flex; flex-direction: column; height: 100vh; background: #1a1a2e; color: #eee;
}
.page-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 24px; background: #16213e; border-bottom: 1px solid #0f3460;
  h1 { margin: 0; font-size: 1.4rem; color: #f6c90e; }
  .btn-logout { background: transparent; border: 1px solid #666; color: #aaa;
                padding: 6px 12px; border-radius: 4px; cursor: pointer; }
}
.builder-layout {
  display: flex; flex: 1; overflow: hidden;
}
.catalog-panel {
  width: 55%; border-right: 1px solid #0f3460; display: flex;
  flex-direction: column; overflow: hidden;
  .search-controls {
    display: flex; gap: 8px; padding: 12px;
    input, select { flex: 1; background: #16213e; border: 1px solid #0f3460;
                    color: #eee; padding: 8px; border-radius: 4px; }
  }
  .catalog-grid {
    flex: 1; overflow-y: auto; display: flex; flex-wrap: wrap;
    gap: 12px; padding: 12px; align-content: flex-start;
  }
}
.editor-panel {
  width: 45%; display: flex; flex-direction: column; overflow: hidden;
  .deck-list-header { padding: 12px; display: flex; align-items: center; gap: 12px;
    h2 { margin: 0; }
    .new-deck-form { display: flex; gap: 6px;
      input { background: #16213e; border: 1px solid #0f3460; color: #eee;
              padding: 6px; border-radius: 4px; }
      button { background: #f6c90e; color: #1a1a2e; border: none; padding: 6px 12px;
               border-radius: 4px; cursor: pointer; font-weight: bold; }
    }
  }
  .deck-tabs {
    display: flex; flex-wrap: wrap; gap: 6px; padding: 0 12px 12px;
    button { background: #16213e; border: 1px solid #0f3460; color: #eee;
             padding: 6px 12px; border-radius: 4px; cursor: pointer;
             &.active { background: #0f3460; border-color: #f6c90e; }
      .valid-dot { color: #4caf50; }
      .invalid-dot { color: #f44336; }
    }
  }
  .no-deck { padding: 24px; color: #666; text-align: center; }
}
```

- [ ] **Step 9: Build**

```bash
cd frontend && npm run build -- --configuration=development 2>&1 | tail -10
```
Expected: Build successful, no TypeScript errors

- [ ] **Step 10: Commit**

```bash
git add frontend/src/app/shared/ \
        frontend/src/app/features/deck-builder/
git commit -m "feat(sprint-2): add CardPreview, DeckEditor, and DeckBuilder split-panel UI"
```

---

## Task 13: Backend Full Build + Lint

- [ ] **Step 1: Run all backend tests**

```bash
cd backend && ./mvnw test -q 2>&1 | tail -20
```
Expected: BUILD SUCCESS, DeckValidatorTest (10 tests) PASS, DeckServiceTest (3 tests) PASS

- [ ] **Step 2: Run frontend lint**

```bash
cd frontend && npm run lint
```
Expected: No errors

- [ ] **Step 3: Run frontend tests**

```bash
cd frontend && npm test
```
Expected: All tests pass (existing app.spec.ts must still pass)

- [ ] **Step 4: Final commit if any fixes needed, then push**

```bash
git push origin develop
```

---

## Definition of Done Checklist

- [ ] `./mvnw test` green — DeckValidatorTest ≥ 10 tests, DeckServiceTest ≥ 3 tests
- [ ] DeckValidator has no Spring imports (verify with `grep -r "springframework" backend/src/main/java/com/utn/pokemontcg/domain/service/DeckValidator.java`)
- [ ] GET `/api/cards/search?name=bulba` responds < 500ms
- [ ] POST `/api/auth/register` returns JWT token
- [ ] POST `/api/auth/login` returns 401 on wrong password
- [ ] GET `/api/decks` returns 401 without Bearer token
- [ ] CardPreviewComponent has no DeckService import (reusable in Sprint 5)
- [ ] Frontend build: `npm run build` succeeds with zero errors
- [ ] `npm run lint` passes
- [ ] All commits on `develop` branch

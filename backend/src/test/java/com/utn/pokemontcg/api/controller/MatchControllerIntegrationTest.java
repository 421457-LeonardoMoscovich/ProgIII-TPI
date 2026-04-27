package com.utn.pokemontcg.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utn.pokemontcg.api.dto.AuthRequest;
import com.utn.pokemontcg.api.ws.GameEventPublisher;
import com.utn.pokemontcg.domain.engine.event.GameEvent;
import com.utn.pokemontcg.domain.model.Card;
import com.utn.pokemontcg.domain.model.User;
import com.utn.pokemontcg.infrastructure.persistence.CardRepository;
import com.utn.pokemontcg.infrastructure.persistence.DeckRepository;
import com.utn.pokemontcg.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MatchControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository userRepo;
    @Autowired DeckRepository deckRepo;
    @Autowired CardRepository cardRepo;
    @Autowired GameEventPublisher eventPublisher;

    private String player1Token;
    private String player2Token;
    private Long player1DeckId;
    private Long player2DeckId;
    private String basicPokemonId;
    private String basicEnergyId;

    @BeforeEach
    void setUp() throws Exception {
        deckRepo.deleteAll();
        userRepo.deleteAll();

        basicPokemonId = cardRepo.findAll().stream()
                .filter(c -> "Pokémon".equals(c.getSupertype()) && c.getSubtypes().contains("Basic"))
                .map(Card::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No Basic Pokémon in DB"));

        basicEnergyId = cardRepo.findAll().stream()
                .filter(c -> "Energy".equals(c.getSupertype()) && c.getSubtypes().contains("Basic"))
                .map(Card::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No Basic Energy in DB"));

        player1Token = register("match_p1", "match_p1@test.com");
        player2Token = register("match_p2", "match_p2@test.com");
        player1DeckId = validDeck(player1Token, "P1 valid");
        player2DeckId = validDeck(player2Token, "P2 valid");
    }

    @Test
    void create_addsWaitingMatchToLobby() throws Exception {
        Long matchId = createMatch(player1Token, player1DeckId);

        mvc.perform(get("/api/matches").header("Authorization", bearer(player1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + matchId + ")].status", hasItem("WAITING")))
                .andExpect(jsonPath("$[?(@.id == " + matchId + ")].player1Username", hasItem("match_p1")));
    }

    @Test
    void create_withInvalidDeck_returnsBadRequest() throws Exception {
        Long deckId = createDeck(player1Token, "Invalid deck");
        putCards(player1Token, deckId, Map.of(basicEnergyId, 60));

        mvc.perform(post("/api/matches")
                        .header("Authorization", bearer(player1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("deckId", deckId))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withAnotherUsersDeck_returnsForbidden() throws Exception {
        mvc.perform(post("/api/matches")
                        .header("Authorization", bearer(player1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("deckId", player2DeckId))))
                .andExpect(status().isForbidden());
    }

    @Test
    void state_waitingMatchReturnsSafeEmptySnapshot() throws Exception {
        Long matchId = createMatch(player1Token, player1DeckId);

        mvc.perform(get("/api/matches/" + matchId + "/state")
                        .header("Authorization", bearer(player1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(matchId))
                .andExpect(jsonPath("$.myPlayerId").isNumber())
                .andExpect(jsonPath("$.myHandCount").value(0))
                .andExpect(jsonPath("$.opponentHandCount").value(0))
                .andExpect(jsonPath("$.myHand").isArray())
                .andExpect(jsonPath("$.myBench").isArray())
                .andExpect(jsonPath("$.opponentBench").isArray());
    }

    @Test
    void join_startsSessionAndStateIsPlayerFiltered() throws Exception {
        Long matchId = createMatch(player1Token, player1DeckId);

        mvc.perform(post("/api/matches/" + matchId + "/join")
                        .header("Authorization", bearer(player2Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("deckId", player2DeckId))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/matches/" + matchId + "/state")
                        .header("Authorization", bearer(player1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(matchId))
                .andExpect(jsonPath("$.myPlayerId").isNumber())
                .andExpect(jsonPath("$.myHandCount", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.opponentHandCount", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.myHand").isArray())
                .andExpect(jsonPath("$.opponentHand").doesNotExist())
                .andExpect(jsonPath("$.opponentBench").isArray());
    }

    @Test
    void state_afterJoinReturnsDistinctPrivateViewsForBothPlayers() throws Exception {
        Long matchId = createMatch(player1Token, player1DeckId);

        mvc.perform(post("/api/matches/" + matchId + "/join")
                        .header("Authorization", bearer(player2Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("deckId", player2DeckId))))
                .andExpect(status().isOk());

        var p1Response = mvc.perform(get("/api/matches/" + matchId + "/state")
                        .header("Authorization", bearer(player1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opponentHand").doesNotExist())
                .andReturn();
        var p2Response = mvc.perform(get("/api/matches/" + matchId + "/state")
                        .header("Authorization", bearer(player2Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opponentHand").doesNotExist())
                .andReturn();

        var p1State = mapper.readTree(p1Response.getResponse().getContentAsString());
        var p2State = mapper.readTree(p2Response.getResponse().getContentAsString());

        assertThat(p1State.get("myPlayerId").asLong()).isNotEqualTo(p2State.get("myPlayerId").asLong());
        assertThat(p1State.get("currentPlayerId").asLong()).isEqualTo(p2State.get("currentPlayerId").asLong());
        assertThat(p1State.get("myHandCount").asInt()).isEqualTo(p1State.get("myHand").size());
        assertThat(p2State.get("myHandCount").asInt()).isEqualTo(p2State.get("myHand").size());
        assertThat(p1State.get("opponentHandCount").asInt()).isEqualTo(p2State.get("myHandCount").asInt());
        assertThat(p2State.get("opponentHandCount").asInt()).isEqualTo(p1State.get("myHandCount").asInt());
    }

    @Test
    void join_selfJoinReturnsBadRequest() throws Exception {
        Long matchId = createMatch(player1Token, player1DeckId);

        mvc.perform(post("/api/matches/" + matchId + "/join")
                        .header("Authorization", bearer(player1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("deckId", player1DeckId))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void join_alreadyActiveMatchReturnsConflict() throws Exception {
        Long matchId = createMatch(player1Token, player1DeckId);
        mvc.perform(post("/api/matches/" + matchId + "/join")
                        .header("Authorization", bearer(player2Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("deckId", player2DeckId))))
                .andExpect(status().isOk());

        String thirdToken = register("match_p3", "match_p3@test.com");
        Long thirdDeckId = validDeck(thirdToken, "P3 valid");

        mvc.perform(post("/api/matches/" + matchId + "/join")
                        .header("Authorization", bearer(thirdToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("deckId", thirdDeckId))))
                .andExpect(status().isConflict());
    }

    @Test
    void state_nonParticipantReturnsForbidden() throws Exception {
        String intruderToken = register("intruder", "intruder@test.com");
        Long matchId = createMatch(player1Token, player1DeckId);

        mvc.perform(get("/api/matches/" + matchId + "/state")
                        .header("Authorization", bearer(intruderToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void reconnect_returnsFilteredSnapshotAndMaskedRecentEvents() throws Exception {
        Long matchId = createMatch(player1Token, player1DeckId);
        mvc.perform(post("/api/matches/" + matchId + "/join")
                        .header("Authorization", bearer(player2Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("deckId", player2DeckId))))
                .andExpect(status().isOk());

        User player1 = userRepo.findByUsername("match_p1").orElseThrow();
        User player2 = userRepo.findByUsername("match_p2").orElseThrow();
        eventPublisher.publish(matchId.toString(), player1.getId(), player2.getId(), List.of(
                new GameEvent.CardDrawn(matchId.toString(), player1.getId(), "hidden-card")
        ));

        mvc.perform(get("/api/matches/" + matchId + "/reconnect")
                        .param("afterSequence", "0")
                        .header("Authorization", bearer(player2Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.matchId").value(matchId))
                .andExpect(jsonPath("$.snapshot.myHand").isArray())
                .andExpect(jsonPath("$.snapshot.opponentHandCount", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.recentEvents[0].type").value("CardDrawn"))
                .andExpect(jsonPath("$.recentEvents[0].payload.userId").value(player1.getId()))
                .andExpect(jsonPath("$.recentEvents[0].payload.cardId").value(nullValue()))
                .andExpect(jsonPath("$.recentEvents[0].sequence").value(1))
                .andExpect(jsonPath("$.currentSequence").value(1));
    }

    private String register(String username, String email) throws Exception {
        var result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AuthRequest(username, email, "pass1234!"))))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private Long validDeck(String token, String name) throws Exception {
        Long deckId = createDeck(token, name);
        putCards(token, deckId, Map.of(basicPokemonId, 4, basicEnergyId, 56));
        mvc.perform(post("/api/decks/" + deckId + "/validate")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
        return deckId;
    }

    private Long createDeck(String token, String name) throws Exception {
        var result = mvc.perform(post("/api/decks")
                        .param("name", name)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void putCards(String token, Long deckId, Map<String, Integer> cards) throws Exception {
        mvc.perform(put("/api/decks/" + deckId + "/cards")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(cards)))
                .andExpect(status().isOk());
    }

    private Long createMatch(String token, Long deckId) throws Exception {
        var result = mvc.perform(post("/api/matches")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("deckId", deckId))))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}

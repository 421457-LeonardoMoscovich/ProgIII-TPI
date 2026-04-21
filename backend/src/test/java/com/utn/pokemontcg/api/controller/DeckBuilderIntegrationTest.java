package com.utn.pokemontcg.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utn.pokemontcg.api.dto.AuthRequest;
import com.utn.pokemontcg.domain.model.Card;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeckBuilderIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository userRepo;
    @Autowired DeckRepository deckRepo;
    @Autowired CardRepository cardRepo;

    private String token;
    private String basicPokemonId;
    private String basicEnergyId;
    private String trainerCardId;

    private static final String USERNAME = "deckbuilder_test";

    @BeforeEach
    void setUp() throws Exception {
        deckRepo.deleteAll();
        userRepo.deleteAll();

        ensureTestCard("test-ace-1", "Carta AS TÁCTICO 1", "Trainer", List.of("ACE SPEC"));
        ensureTestCard("test-ace-2", "Carta AS TÁCTICO 2", "Trainer", List.of("ACE SPEC"));

        basicPokemonId = cardRepo.findAll().stream()
                .filter(c -> "Pokémon".equals(c.getSupertype()) && c.getSubtypes().contains("Basic"))
                .map(Card::getId).findFirst()
                .orElseThrow(() -> new IllegalStateException("No Basic Pokémon in DB — card cache not loaded"));

        basicEnergyId = cardRepo.findAll().stream()
                .filter(c -> "Energy".equals(c.getSupertype()) && c.getSubtypes().contains("Basic"))
                .map(Card::getId).findFirst()
                .orElseThrow(() -> new IllegalStateException("No Basic Energy in DB"));

        trainerCardId = cardRepo.findAll().stream()
                .filter(c -> "Trainer".equals(c.getSupertype()) && !c.getSubtypes().contains("ACE SPEC"))
                .map(Card::getId).findFirst()
                .orElseThrow(() -> new IllegalStateException("No Trainer card in DB"));

        var reg = new AuthRequest(USERNAME, USERNAME + "@test.com", "pass1234!");
        var result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(reg)))
                .andExpect(status().isOk())
                .andReturn();
        token = mapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private void ensureTestCard(String id, String name, String supertype, List<String> subtypes) {
        if (cardRepo.findById(id).isEmpty()) {
            cardRepo.save(new Card(id, "xy1", "999", name, supertype, subtypes,
                    null, List.of(), List.of(), null, null, null, null,
                    null, null, null, mapper.createObjectNode()));
        }
    }

    private long createDeck(String name) throws Exception {
        var result = mvc.perform(post("/api/decks")
                        .param("name", name)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void putCards(long deckId, Map<String, Integer> cards) throws Exception {
        mvc.perform(put("/api/decks/" + deckId + "/cards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(cards)))
                .andExpect(status().isOk());
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    void happyPath_createUpdateValidateRetrieve() throws Exception {
        long id = createDeck("Mi Mazo XY1");

        // 4 basic pokemon + 56 basic energy = 60 valid cards
        putCards(id, Map.of(basicPokemonId, 4, basicEnergyId, 56));

        mvc.perform(post("/api/decks/" + id + "/validate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.errors", empty()));

        mvc.perform(get("/api/decks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].name",
                        hasItem("Mi Mazo XY1")));
    }

    @Test
    void validate_59cards_rejectsWithCountMessage() throws Exception {
        long id = createDeck("Incompleto");
        putCards(id, Map.of(basicPokemonId, 3, basicEnergyId, 56)); // 59 total

        mvc.perform(post("/api/decks/" + id + "/validate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0]", containsString("59")));
    }

    @Test
    void validate_5copiesOfNonEnergy_rejectsWithCopyMessage() throws Exception {
        long id = createDeck("Demasiadas Copias");
        // 5 trainer + 4 basic pokemon + 51 basic energy = 60
        putCards(id, Map.of(trainerCardId, 5, basicPokemonId, 4, basicEnergyId, 51));

        mvc.perform(post("/api/decks/" + id + "/validate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[*]", hasItem(containsString("máximo 4"))));
    }

    @Test
    void validate_noBasicPokemon_rejectsWithPokemonMessage() throws Exception {
        long id = createDeck("Solo Energía");
        putCards(id, Map.of(basicEnergyId, 60)); // no pokemon at all

        mvc.perform(post("/api/decks/" + id + "/validate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[*]", hasItem(containsString("Básico"))));
    }

    @Test
    void validate_2aceSpec_rejectsWithAceSpecMessage() throws Exception {
        long id = createDeck("Doble AS TÁCTICO");
        // 1 ace1 + 1 ace2 + 4 basic pokemon + 54 basic energy = 60
        putCards(id, Map.of("test-ace-1", 1, "test-ace-2", 1, basicPokemonId, 4, basicEnergyId, 54));

        mvc.perform(post("/api/decks/" + id + "/validate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[*]", hasItem(containsString("AS TÁCTICO"))));
    }

    @Test
    void delete_removesFromList() throws Exception {
        long id = createDeck("Para Borrar");

        mvc.perform(delete("/api/decks/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(get("/api/decks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]", empty()));
    }

    @Test
    void otherUser_cannotValidateOrDeleteAnotherUsersDeck() throws Exception {
        long id = createDeck("Mazo Privado");
        putCards(id, Map.of(basicPokemonId, 4, basicEnergyId, 56));

        // Register second user
        var reg2 = new AuthRequest("otro_user", "otro@test.com", "pass1234!");
        var r2 = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(reg2)))
                .andReturn();
        var token2 = mapper.readTree(r2.getResponse().getContentAsString()).get("token").asText();

        mvc.perform(post("/api/decks/" + id + "/validate")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/decks/" + id)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isForbidden());
    }
}

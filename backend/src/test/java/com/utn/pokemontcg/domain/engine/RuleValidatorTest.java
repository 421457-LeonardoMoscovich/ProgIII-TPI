package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.action.GameAction;
import com.utn.pokemontcg.domain.engine.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleValidatorTest {

    private RuleValidator validator;
    private PlayerState player1;
    private PlayerState player2;
    private GameState state;

    // ── helpers ─────────────────────────────────────────────────────────────

    private GameCard basicCard(String id, int retreatCost) {
        return new GameCard(id, "Bulbasaur", "Pokémon", List.of("Basic"),
                60, List.of(Map.of("name", "Tackle", "damage", "10")),
                null, null, retreatCost);
    }

    private GameCard basicCard(String id) {
        return basicCard(id, 1);
    }

    private GameCard energyCard(String id) {
        return new GameCard(id, "Grass Energy", "Energy", List.of("Basic Energy"),
                0, List.of(), null, null, 0);
    }

    private GameCard trainerCard(String id) {
        return new GameCard(id, "Potion", "Trainer", List.of("Item"),
                0, List.of(), null, null, 0);
    }

    private PokemonInPlay activePokemon(String cardId) {
        return activePokemon(cardId, 1);
    }

    private PokemonInPlay activePokemon(String cardId, int retreatCost) {
        return new PokemonInPlay(basicCard(cardId, retreatCost));
    }

    // ── setup ────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        validator = new RuleValidator();
        player1 = new PlayerState(1L);
        player2 = new PlayerState(2L);
        state = new GameState("match-1", player1, player2);
        // currentPlayer is player1 by default
    }

    // ── Attack tests ─────────────────────────────────────────────────────────

    @Test
    void attack_blockedOnFirstTurn() {
        // globalTurn == 0, currentPlayer is player1
        PokemonInPlay active = activePokemon("card-1");
        active.clearJustPlaced();
        player1.setActivePokemon(active);

        var action = new GameAction.Attack(1L, 0);
        var result = validator.validate(action, state);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("first turn");
    }

    @Test
    void attack_blockedWhenParalizado() {
        state.swapTurn(); // globalTurn = 1, now player2's turn
        state.swapTurn(); // globalTurn = 2, back to player1

        PokemonInPlay active = activePokemon("card-1");
        active.clearJustPlaced();
        active.setPrimaryStatus(StatusCondition.PARALIZADO);
        player1.setActivePokemon(active);

        var action = new GameAction.Attack(1L, 0);
        var result = validator.validate(action, state);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("paraliz");
    }

    @Test
    void attack_blockedWhenDormido() {
        state.swapTurn();
        state.swapTurn();

        PokemonInPlay active = activePokemon("card-1");
        active.clearJustPlaced();
        active.setPrimaryStatus(StatusCondition.DORMIDO);
        player1.setActivePokemon(active);

        var action = new GameAction.Attack(1L, 0);
        var result = validator.validate(action, state);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("dormido");
    }

    @Test
    void attack_blockedWhenAlreadyAttackedThisTurn() {
        state.swapTurn();
        state.swapTurn();

        PokemonInPlay active = activePokemon("card-1");
        active.clearJustPlaced();
        player1.setActivePokemon(active);
        state.getTurnFlags().setAttackDoneThisTurn(true);

        var action = new GameAction.Attack(1L, 0);
        var result = validator.validate(action, state);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("already attacked");
    }

    @Test
    void attack_allowedOnTurnOneOrMoreWithNoStatus() {
        state.swapTurn(); // globalTurn = 1, currentPlayer = player2
        PokemonInPlay active = activePokemon("card-2");
        active.clearJustPlaced();
        player2.setActivePokemon(active);

        var action = new GameAction.Attack(2L, 0);
        var result = validator.validate(action, state);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void attack_allowedWhenConfused() {
        state.swapTurn();
        state.swapTurn();

        PokemonInPlay active = activePokemon("card-1");
        active.clearJustPlaced();
        active.setPrimaryStatus(StatusCondition.CONFUNDIDO);
        player1.setActivePokemon(active);

        var action = new GameAction.Attack(1L, 0);
        var result = validator.validate(action, state);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void attack_blockedWhenNoActivePokemon() {
        state.swapTurn();
        state.swapTurn();
        // active pokemon is null (not set)

        var action = new GameAction.Attack(1L, 0);
        var result = validator.validate(action, state);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("no active");
    }

    @Test
    void retreat_blockedWhenNoActivePokemon() {
        // active pokemon is null (not set)

        var action = new GameAction.Retreat(1L, List.of());
        var result = validator.validate(action, state);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("no active");
    }

    // ── Retreat tests ─────────────────────────────────────────────────────────

    @Test
    void retreat_blockedWhenParalizado() {
        PokemonInPlay active = activePokemon("card-1", 1);
        active.clearJustPlaced();
        active.setPrimaryStatus(StatusCondition.PARALIZADO);
        active.getAttachedEnergyIds().add("energy-1");
        player1.setActivePokemon(active);

        // Add a bench pokemon so retreat target exists
        player1.getBench().add(activePokemon("bench-1"));

        var action = new GameAction.Retreat(1L, List.of("energy-1"));
        var result = validator.validate(action, state);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("paraliz");
    }

    @Test
    void retreat_blockedWhenDormido() {
        PokemonInPlay active = activePokemon("card-1", 1);
        active.clearJustPlaced();
        active.setPrimaryStatus(StatusCondition.DORMIDO);
        active.getAttachedEnergyIds().add("energy-1");
        player1.setActivePokemon(active);

        player1.getBench().add(activePokemon("bench-1"));

        var action = new GameAction.Retreat(1L, List.of("energy-1"));
        var result = validator.validate(action, state);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("dormido");
    }

    @Test
    void retreat_blockedWhenNotEnoughEnergy() {
        PokemonInPlay active = activePokemon("card-1", 2); // needs 2 energy
        active.clearJustPlaced();
        // Only 1 energy attached but discarding 1
        active.getAttachedEnergyIds().add("energy-1");
        player1.setActivePokemon(active);

        player1.getBench().add(activePokemon("bench-1"));

        var action = new GameAction.Retreat(1L, List.of("energy-1"));
        var result = validator.validate(action, state);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("energy");
    }

    @Test
    void retreat_blockedWhenAlreadyRetreatedThisTurn() {
        PokemonInPlay active = activePokemon("card-1", 1);
        active.clearJustPlaced();
        active.getAttachedEnergyIds().add("energy-1");
        player1.setActivePokemon(active);

        player1.getBench().add(activePokemon("bench-1"));
        state.getTurnFlags().setRetreatedThisTurn(true);

        var action = new GameAction.Retreat(1L, List.of("energy-1"));
        var result = validator.validate(action, state);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("already retreated");
    }

    @Test
    void retreat_allowedWithEnoughEnergy() {
        PokemonInPlay active = activePokemon("card-1", 1);
        active.clearJustPlaced();
        active.getAttachedEnergyIds().add("energy-1");
        player1.setActivePokemon(active);

        player1.getBench().add(activePokemon("bench-1"));

        var action = new GameAction.Retreat(1L, List.of("energy-1"));
        var result = validator.validate(action, state);

        assertThat(result.valid()).isTrue();
    }

    // ── AttachEnergy tests ────────────────────────────────────────────────────

    @Test
    void attachEnergy_blockedWhenAlreadyAttachedThisTurn() {
        player1.getHand().add(energyCard("energy-1"));
        PokemonInPlay active = activePokemon("card-1");
        player1.setActivePokemon(active);
        state.getTurnFlags().setEnergyAttachedThisTurn(true);

        var action = new GameAction.AttachEnergy(1L, "energy-1", "card-1");
        var result = validator.validate(action, state);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("energy");
    }

    @Test
    void attachEnergy_allowedWhenNotYetAttached() {
        player1.getHand().add(energyCard("energy-1"));
        PokemonInPlay active = activePokemon("card-1");
        player1.setActivePokemon(active);

        var action = new GameAction.AttachEnergy(1L, "energy-1", "card-1");
        var result = validator.validate(action, state);

        assertThat(result.valid()).isTrue();
    }

    // ── Evolve tests ──────────────────────────────────────────────────────────

    @Test
    void evolve_blockedWhenTargetJustPlaced() {
        // target in play was justPlaced (not clearJustPlaced called)
        PokemonInPlay active = activePokemon("card-1"); // justPlaced = true by default
        player1.setActivePokemon(active);

        GameCard evolutionCard = new GameCard("evo-1", "Ivysaur", "Pokémon", List.of("Stage 1"),
                80, List.of(), null, null, 1);
        player1.getHand().add(evolutionCard);

        var action = new GameAction.Evolve(1L, "evo-1", "card-1");
        var result = validator.validate(action, state);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("just placed");
    }

    @Test
    void evolve_blockedWhenEvolutionCardNotInHand() {
        PokemonInPlay active = activePokemon("card-1");
        active.clearJustPlaced();
        player1.setActivePokemon(active);
        // evolution card NOT in hand

        var action = new GameAction.Evolve(1L, "evo-1", "card-1");
        var result = validator.validate(action, state);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("hand");
    }

    // ── PlayBasicPokemon tests ────────────────────────────────────────────────

    @Test
    void playBasicPokemon_blockedWhenBenchFull() {
        // Fill bench with 5 pokemon
        for (int i = 0; i < 5; i++) {
            player1.getBench().add(activePokemon("bench-" + i));
        }
        GameCard card = basicCard("card-1");
        player1.getHand().add(card);

        var action = new GameAction.PlayBasicPokemon(1L, "card-1", true);
        var result = validator.validate(action, state);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("bench");
    }

    @Test
    void playBasicPokemon_blockedWhenCardNotInHand() {
        // card not in hand
        var action = new GameAction.PlayBasicPokemon(1L, "card-1", true);
        var result = validator.validate(action, state);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("hand");
    }

    @Test
    void playBasicPokemon_allowedWhenBenchHasRoom() {
        GameCard card = basicCard("card-1");
        player1.getHand().add(card);
        // bench has fewer than 5

        var action = new GameAction.PlayBasicPokemon(1L, "card-1", true);
        var result = validator.validate(action, state);

        assertThat(result.valid()).isTrue();
    }

    // ── PlayTrainer tests ─────────────────────────────────────────────────────

    @Test
    void playTrainer_blockedWhenCardNotInHand() {
        var action = new GameAction.PlayTrainer(1L, "trainer-1");
        var result = validator.validate(action, state);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("hand");
    }

    @Test
    void playTrainer_allowedWhenCardInHand() {
        player1.getHand().add(trainerCard("trainer-1"));

        var action = new GameAction.PlayTrainer(1L, "trainer-1");
        var result = validator.validate(action, state);

        assertThat(result.valid()).isTrue();
    }

    // ── Pass tests ────────────────────────────────────────────────────────────

    @Test
    void pass_alwaysValid() {
        var action = new GameAction.Pass(1L);
        var result = validator.validate(action, state);

        assertThat(result.valid()).isTrue();
        assertThat(result.reason()).isNull();
    }
}

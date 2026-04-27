package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.action.GameAction;
import com.utn.pokemontcg.domain.engine.event.GameEvent;
import com.utn.pokemontcg.domain.engine.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD spec for GameEngineFacade (ENG-10) — the single public API of the game engine.
 */
class GameEngineFacadeTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    private static final long SEED = 42L;

    private GameCard basicPokemon(String id, String name, int hp) {
        return new GameCard(id, name, "Pokémon", List.of("Basic"), hp,
                List.of(Map.of("name", "Tackle", "cost", List.of("Colorless"), "damage", "10")),
                null, null, 1);
    }

    private GameCard energyCard(String id) {
        return new GameCard(id, "Colorless Energy", "Energy", List.of("Colorless"),
                0, List.of(), null, null, 0);
    }

    private GameCard trainerCard(String id) {
        return new GameCard(id, "Potion", "Trainer", List.of("Item"),
                0, List.of(), null, null, 0);
    }

    private GameCard stageOnePokemon(String id, String name, int hp) {
        return new GameCard(id, name, "Pokémon", List.of("Stage 1"), hp,
                List.of(Map.of("name", "Heavy Hit", "cost", List.of("Colorless"), "damage", "30")),
                null, null, 1);
    }

    /** 60-card deck: 30 basics + 30 energies. Always has basics for opening hand. */
    private List<GameCard> buildDeck(String prefix) {
        List<GameCard> deck = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            deck.add(basicPokemon(prefix + "-poke-" + i, "Poke" + i, 60));
        }
        for (int i = 1; i <= 30; i++) {
            deck.add(energyCard(prefix + "-energy-" + i));
        }
        return deck;
    }

    // ════════════════════════════════════════════════════════════════════════
    // startMatch
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void startMatch_returns_active_game_state_with_hands_and_prizes() {
        GameEngineFacade facade = new GameEngineFacade();

        GameState state = facade.startMatch(1L, buildDeck("p1"), 2L, buildDeck("p2"), SEED);

        assertThat(state).isNotNull();
        assertThat(state.getMatchPhase()).isEqualTo(MatchPhase.ACTIVE);
        assertThat(state.getPlayer1().getHand()).hasSize(7);
        assertThat(state.getPlayer2().getHand()).hasSize(7);
        assertThat(state.getPlayer1().getPrizes()).hasSize(6);
        assertThat(state.getPlayer2().getPrizes()).hasSize(6);
        assertThat(state.getPlayer1().getDeck()).hasSize(47);
        assertThat(state.getPlayer2().getDeck()).hasSize(47);
    }

    @Test
    void startMatch_emits_match_started_event() {
        GameEngineFacade facade = new GameEngineFacade();
        facade.startMatch(1L, buildDeck("p1"), 2L, buildDeck("p2"), SEED);

        List<GameEvent> events = facade.getLastEvents();

        assertThat(events).anyMatch(e ->
                e instanceof GameEvent.MatchStarted ms
                && ms.player1Id().equals(1L)
                && ms.player2Id().equals(2L));
    }

    @Test
    void startMatch_first_player_begins_in_main_phase_skipping_draw() {
        GameEngineFacade facade = new GameEngineFacade();
        GameState state = facade.startMatch(1L, buildDeck("p1"), 2L, buildDeck("p2"), SEED);

        // player1 goes first; first player skips DRAW on turn 0
        assertThat(state.getTurnPhase()).isEqualTo(TurnPhase.MAIN);
        assertThat(state.getCurrentPlayer().getUserId()).isEqualTo(1L);
    }

    // ════════════════════════════════════════════════════════════════════════
    // applyAction — PlayBasicPokemon (place active)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void applyAction_play_basic_to_active_slot_succeeds() {
        GameEngineFacade facade = new GameEngineFacade();
        GameState state = facade.startMatch(1L, buildDeck("p1"), 2L, buildDeck("p2"), SEED);

        // Find a basic pokemon in p1's hand
        GameCard cardToPlay = state.getPlayer1().getHand().stream()
                .filter(c -> "Pokémon".equals(c.supertype()))
                .findFirst()
                .orElseThrow();

        GameAction action = new GameAction.PlayBasicPokemon(
                state.getCurrentPlayer().getUserId(), cardToPlay.id(), false);

        ActionResult result = facade.applyAction(state, action);

        assertThat(result.isOk()).isTrue();
        assertThat(state.getPlayer1().getActivePokemon()).isNotNull();
        assertThat(state.getPlayer1().getActivePokemon().getCard().id()).isEqualTo(cardToPlay.id());
        assertThat(state.getPlayer1().getHand()).noneMatch(c -> c.id().equals(cardToPlay.id()));
    }

    @Test
    void applyAction_rejected_when_wrong_player_acts() {
        GameEngineFacade facade = new GameEngineFacade();
        GameState state = facade.startMatch(1L, buildDeck("p1"), 2L, buildDeck("p2"), SEED);

        // p2 tries to act when it's p1's turn
        GameCard card = state.getPlayer2().getHand().stream()
                .filter(c -> "Pokémon".equals(c.supertype()))
                .findFirst()
                .orElseThrow();
        GameAction action = new GameAction.PlayBasicPokemon(2L, card.id(), false);

        ActionResult result = facade.applyAction(state, action);

        assertThat(result.isOk()).isFalse();
        assertThat(result.getRejectionReason()).isNotBlank();
    }

    // ════════════════════════════════════════════════════════════════════════
    // applyAction — Attack
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void applyAction_attack_deals_damage_to_defender() {
        GameEngineFacade facade = new GameEngineFacade();
        GameState state = facade.startMatch(1L, buildDeck("p1"), 2L, buildDeck("p2"), SEED);

        // Wire up actives directly
        GameCard atkCard = basicPokemon("atk-1", "Attacker", 60);
        GameCard defCard = basicPokemon("def-1", "Defender", 60);
        PokemonInPlay attacker = new PokemonInPlay(atkCard);
        attacker.clearJustPlaced();
        attacker.getAttachedEnergyIds().add("Colorless");
        PokemonInPlay defender = new PokemonInPlay(defCard);
        defender.clearJustPlaced();

        state.getPlayer1().setActivePokemon(attacker);
        state.getPlayer2().setActivePokemon(defender);
        state.setTurnPhase(TurnPhase.ATTACK);

        // Attack index 0 = "Tackle" (10 damage, costs Colorless)
        GameAction action = new GameAction.Attack(1L, 0);

        ActionResult result = facade.applyAction(state, action);

        assertThat(result.isOk()).isTrue();
        assertThat(defender.getDamage()).isEqualTo(10);
        assertThat(state.getCurrentPlayer().getUserId()).isEqualTo(2L);
        assertThat(facade.getLastEvents()).anyMatch(e -> e instanceof GameEvent.TurnEnded);
    }

    @Test
    void applyAction_pass_advances_to_next_player() {
        GameEngineFacade facade = new GameEngineFacade();
        GameState state = facade.startMatch(1L, buildDeck("p1"), 2L, buildDeck("p2"), SEED);

        ActionResult result = facade.applyAction(state, new GameAction.Pass(1L));

        assertThat(result.isOk()).isTrue();
        assertThat(state.getCurrentPlayer().getUserId()).isEqualTo(2L);
        assertThat(facade.getLastEvents()).anyMatch(e -> e instanceof GameEvent.TurnEnded);
    }

    @Test
    void applyAction_playTrainer_discards_card_and_emits_event() {
        GameEngineFacade facade = new GameEngineFacade();
        GameState state = facade.startMatch(1L, buildDeck("p1"), 2L, buildDeck("p2"), SEED);
        GameCard trainer = trainerCard("trainer-1");
        state.getPlayer1().getHand().add(trainer);

        ActionResult result = facade.applyAction(state, new GameAction.PlayTrainer(1L, trainer.id()));

        assertThat(result.isOk()).isTrue();
        assertThat(state.getPlayer1().getHand()).doesNotContain(trainer);
        assertThat(state.getPlayer1().getDiscardPile()).contains(trainer);
        assertThat(facade.getLastEvents()).anyMatch(e -> e instanceof GameEvent.TrainerPlayed);
    }

    @Test
    void applyAction_evolve_replaces_target_and_preserves_damage_energy_tools() {
        GameEngineFacade facade = new GameEngineFacade();
        GameState state = facade.startMatch(1L, buildDeck("p1"), 2L, buildDeck("p2"), SEED);
        PokemonInPlay basic = new PokemonInPlay(basicPokemon("basic-1", "Basic", 60));
        basic.clearJustPlaced();
        basic.addDamage(20);
        basic.getAttachedEnergyIds().add("Colorless");
        state.getPlayer1().setActivePokemon(basic);
        GameCard evolution = stageOnePokemon("stage-1", "Stage", 90);
        state.getPlayer1().getHand().add(evolution);

        ActionResult result = facade.applyAction(state, new GameAction.Evolve(1L, evolution.id(), basic.getCard().id()));

        assertThat(result.isOk()).isTrue();
        assertThat(state.getPlayer1().getActivePokemon().getCard().id()).isEqualTo(evolution.id());
        assertThat(state.getPlayer1().getActivePokemon().getDamage()).isEqualTo(20);
        assertThat(state.getPlayer1().getActivePokemon().getAttachedEnergyIds()).containsExactly("Colorless");
        assertThat(facade.getLastEvents()).anyMatch(e -> e instanceof GameEvent.PokemonEvolved);
    }

    @Test
    void applyAction_retreat_promotes_first_bench_and_emits_event() {
        GameEngineFacade facade = new GameEngineFacade();
        GameState state = facade.startMatch(1L, buildDeck("p1"), 2L, buildDeck("p2"), SEED);
        PokemonInPlay active = new PokemonInPlay(basicPokemon("active-retreat", "Active", 60));
        active.getAttachedEnergyIds().add("Colorless");
        PokemonInPlay bench = new PokemonInPlay(basicPokemon("bench-promote", "Bench", 60));
        state.getPlayer1().setActivePokemon(active);
        state.getPlayer1().getBench().add(bench);

        ActionResult result = facade.applyAction(state, new GameAction.Retreat(1L, List.of("energy-1")));

        assertThat(result.isOk()).isTrue();
        assertThat(state.getPlayer1().getActivePokemon().getCard().id()).isEqualTo("bench-promote");
        assertThat(state.getPlayer1().getBench()).contains(active);
        assertThat(facade.getLastEvents()).anyMatch(e -> e instanceof GameEvent.PokemonRetreated);
    }

    // ════════════════════════════════════════════════════════════════════════
    // processBetweenTurns
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void processBetweenTurns_advances_turn_counter_and_swaps_current_player() {
        GameEngineFacade facade = new GameEngineFacade();
        GameState state = facade.startMatch(1L, buildDeck("p1"), 2L, buildDeck("p2"), SEED);

        state.setTurnPhase(TurnPhase.BETWEEN_TURNS);
        int turnBefore = state.getGlobalTurn();
        Long playerBefore = state.getCurrentPlayer().getUserId();

        List<GameEvent> events = facade.processBetweenTurns(state);

        assertThat(state.getGlobalTurn()).isEqualTo(turnBefore + 1);
        assertThat(state.getCurrentPlayer().getUserId()).isNotEqualTo(playerBefore);
        assertThat(events).anyMatch(e -> e instanceof GameEvent.TurnEnded);
    }

    @Test
    void processBetweenTurns_applies_poison_damage_to_current_player_active() {
        GameEngineFacade facade = new GameEngineFacade();
        GameState state = facade.startMatch(1L, buildDeck("p1"), 2L, buildDeck("p2"), SEED);

        GameCard card = basicPokemon("poke-x", "Ivysaur", 90);
        PokemonInPlay pip = new PokemonInPlay(card);
        pip.clearJustPlaced();
        pip.setPoisoned(true);
        state.getPlayer1().setActivePokemon(pip);

        state.setTurnPhase(TurnPhase.BETWEEN_TURNS);
        facade.processBetweenTurns(state);

        assertThat(pip.getDamage()).isEqualTo(10);
    }

    // ════════════════════════════════════════════════════════════════════════
    // checkVictory
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void checkVictory_returns_empty_while_game_in_progress() {
        GameEngineFacade facade = new GameEngineFacade();
        GameState state = facade.startMatch(1L, buildDeck("p1"), 2L, buildDeck("p2"), SEED);

        // Both players need an active pokemon in play for the game to be in progress
        GameCard c1 = basicPokemon("active-1", "Bulbasaur", 60);
        GameCard c2 = basicPokemon("active-2", "Charmander", 50);
        state.getPlayer1().setActivePokemon(new PokemonInPlay(c1));
        state.getPlayer2().setActivePokemon(new PokemonInPlay(c2));

        Optional<VictoryConditionChecker.VictoryResult> result = facade.checkVictory(state);

        assertThat(result).isEmpty();
    }

    @Test
    void checkVictory_returns_winner_when_player_takes_all_prizes() {
        GameEngineFacade facade = new GameEngineFacade();
        GameState state = facade.startMatch(1L, buildDeck("p1"), 2L, buildDeck("p2"), SEED);

        // p1 has no prizes left → p1 wins
        state.getPlayer1().getPrizes().clear();

        Optional<VictoryConditionChecker.VictoryResult> result = facade.checkVictory(state);

        assertThat(result).isPresent();
        assertThat(result.get().winnerId()).isEqualTo(1L);
        assertThat(result.get().reason()).isEqualTo("all prizes taken");
    }
}

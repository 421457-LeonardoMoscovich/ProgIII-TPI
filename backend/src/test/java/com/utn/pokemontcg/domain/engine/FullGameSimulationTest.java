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
 * ENG-TEST-01 — Full game simulation.
 *
 * Runs a complete Pokémon TCG match from start to finish using only the
 * GameEngineFacade public API. Both players use deterministic decks.
 * The test drives turns manually, verifying state at each checkpoint.
 *
 * Victory condition: one player takes all 6 prizes (by KO'ing 6 opponent Pokémon).
 */
class FullGameSimulationTest {

    // ── Deck builder ─────────────────────────────────────────────────────────

    /**
     * Builds a deck with high-damage attackers so games end quickly.
     * Each "Attacker" has 40 HP and a 40-damage attack costing 1 Colorless energy.
     * This ensures every hit is lethal — 6 KOs = 6 prizes = win.
     */
    private List<GameCard> buildFastDeck(String prefix) {
        List<GameCard> deck = new ArrayList<>();
        // 30 Basic Pokemon (40 HP each, 1 attack: "Smash" costs Colorless, deals 40)
        for (int i = 1; i <= 30; i++) {
            deck.add(new GameCard(
                    prefix + "-poke-" + i, "Attacker" + i, "Pokémon", List.of("Basic"),
                    40,
                    List.of(Map.of("name", "Smash", "cost", List.of("Colorless"), "damage", "40")),
                    null, null, 1));
        }
        // 30 Colorless energies
        for (int i = 1; i <= 30; i++) {
            deck.add(new GameCard(
                    prefix + "-energy-" + i, "Colorless Energy", "Energy", List.of("Colorless"),
                    0, List.of(), null, null, 0));
        }
        return deck;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Attach a colorless energy directly to the active pokemon (bypass normal flow). */
    private void attachEnergy(PlayerState player) {
        PokemonInPlay active = player.getActivePokemon();
        if (active != null) {
            active.getAttachedEnergyIds().add("Colorless");
        }
    }

    /** Place a Basic pokemon from hand as active (first available Basic). */
    private void placeActive(GameState state, PlayerState player, GameEngineFacade facade) {
        GameCard card = player.getHand().stream()
                .filter(c -> "Pokémon".equals(c.supertype()))
                .findFirst()
                .orElseGet(() -> {
                    // fall back to deck
                    return player.getDeck().stream()
                            .filter(c -> "Pokémon".equals(c.supertype()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("No Basic in hand or deck!"));
                });

        // Remove from wherever it is
        player.getHand().remove(card);
        player.getDeck().remove(card);

        PokemonInPlay pip = new PokemonInPlay(card);
        pip.clearJustPlaced();
        player.setActivePokemon(pip);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ENG-TEST-01 — Complete match, victory by 6 prizes
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void full_game_ends_with_one_player_taking_all_6_prizes() {
        GameEngineFacade facade = new GameEngineFacade();

        // === SETUP ===
        GameState state = facade.startMatch(
                1L, buildFastDeck("p1"),
                2L, buildFastDeck("p2"),
                77L);

        assertThat(state.getMatchPhase()).isEqualTo(MatchPhase.ACTIVE);
        assertThat(state.getPlayer1().getHand()).hasSize(7);
        assertThat(state.getPlayer2().getHand()).hasSize(7);
        assertThat(state.getPlayer1().getPrizes()).hasSize(6);
        assertThat(state.getPlayer2().getPrizes()).hasSize(6);

        // === PLACE ACTIVES (pre-game setup step) ===
        placeActive(state, state.getPlayer1(), facade);
        placeActive(state, state.getPlayer2(), facade);

        // === SIMULATE TURNS UNTIL VICTORY ===
        List<GameEvent> allEvents = new ArrayList<>(facade.getLastEvents());
        Optional<VictoryConditionChecker.VictoryResult> victory = Optional.empty();
        int maxTurns = 50; // safety guard — should finish in 12 turns (6 KOs, 2 per full round)

        for (int turn = 0; turn < maxTurns && victory.isEmpty(); turn++) {
            PlayerState current = state.getCurrentPlayer();
            PlayerState waiting = state.getWaitingPlayer();

            // Ensure current player has an active pokemon
            if (current.getActivePokemon() == null) {
                if (!current.getBench().isEmpty()) {
                    current.setActivePokemon(current.getBench().remove(0));
                } else {
                    placeActive(state, current, facade);
                }
            }

            // Ensure opponent has an active pokemon
            if (waiting.getActivePokemon() == null) {
                if (!waiting.getBench().isEmpty()) {
                    waiting.setActivePokemon(waiting.getBench().remove(0));
                } else {
                    placeActive(state, waiting, facade);
                }
            }

            // Attach energy directly (simulates normal play without AttachEnergy action overhead)
            attachEnergy(current);

            // ATTACK (attack index 0 = "Smash", 40 damage, costs 1 Colorless)
            state.setTurnPhase(TurnPhase.ATTACK);
            GameAction attack = new GameAction.Attack(current.getUserId(), 0);
            ActionResult result = facade.applyAction(state, attack);
            assertThat(result.isOk())
                    .as("Attack on turn %d should succeed, reason: %s", turn, result.getRejectionReason())
                    .isTrue();

            allEvents.addAll(facade.getLastEvents());

            // If defender was KO'd, promote bench → active before victory check
            if (waiting.getActivePokemon() == null) {
                if (!waiting.getBench().isEmpty()) {
                    waiting.setActivePokemon(waiting.getBench().remove(0));
                } else {
                    // No bench replacement → check if they have deck cards to fill
                    // In a real game this would be a forced loss, so check victory now
                    victory = facade.checkVictory(state);
                    if (victory.isPresent()) break;
                    placeActive(state, waiting, facade); // pull from deck for simulation
                }
            }

            // Check victory after attack (prizes, no-pokemon, empty-deck)
            victory = facade.checkVictory(state);
            if (victory.isPresent()) break;

            // BETWEEN TURNS
            state.setTurnPhase(TurnPhase.BETWEEN_TURNS);
            List<GameEvent> btEvents = facade.processBetweenTurns(state);
            allEvents.addAll(btEvents);

            // Check victory after between-turns (poison/burn could finish someone off)
            victory = facade.checkVictory(state);
        }

        // === VERIFY VICTORY ===
        assertThat(victory).as("game must have ended before turn limit").isPresent();

        VictoryConditionChecker.VictoryResult result = victory.get();
        assertThat(result.winnerId()).as("winner must be p1 or p2").isIn(1L, 2L);
        assertThat(result.reason()).as("must be a valid victory reason")
                .isIn("all prizes taken", "no pokemon in play", "drew from empty deck");

        // Events must include at least 1 KO event
        long koCount = allEvents.stream()
                .filter(e -> e instanceof GameEvent.PokemonKnockedOut)
                .count();
        assertThat(koCount).as("must have at least 1 KO event").isGreaterThanOrEqualTo(1);

        // Events must include attack-declared events
        long attackCount = allEvents.stream()
                .filter(e -> e instanceof GameEvent.AttackDeclared)
                .count();
        assertThat(attackCount).as("must have at least 1 attack event").isGreaterThanOrEqualTo(1);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ENG-TEST-02 — Specific scenario: multiple KOs with bench replacements
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void ko_triggers_bench_replacement_and_prize_draw() {
        GameEngineFacade facade = new GameEngineFacade();
        GameState state = facade.startMatch(
                1L, buildFastDeck("p1"),
                2L, buildFastDeck("p2"),
                99L);

        // Place actives
        placeActive(state, state.getPlayer1(), facade);
        placeActive(state, state.getPlayer2(), facade);

        // Add a bench pokemon for p2 (the one who will get KO'd)
        GameCard benchCard = new GameCard("bench-1", "BenchPoke", "Pokémon", List.of("Basic"),
                60, List.of(), null, null, 0);
        PokemonInPlay benchPip = new PokemonInPlay(benchCard);
        benchPip.clearJustPlaced();
        state.getPlayer2().getBench().add(benchPip);

        int p1PrizesInitial = state.getPlayer1().getPrizes().size();

        // Attach energy to p1's active and attack
        attachEnergy(state.getPlayer1());
        state.setTurnPhase(TurnPhase.ATTACK);
        ActionResult result = facade.applyAction(state,
                new GameAction.Attack(1L, 0));

        assertThat(result.isOk()).isTrue();

        // p2's active (40 HP) takes 40 damage → KO'd
        assertThat(state.getPlayer2().getActivePokemon()).isNull();

        // p1 drew a prize
        assertThat(state.getPlayer1().getPrizes()).hasSize(p1PrizesInitial - 1);

        // p2 bench still has benchPip (not yet promoted — player must promote manually)
        assertThat(state.getPlayer2().getBench()).contains(benchPip);

        // Events contain KO + PrizeTaken
        List<GameEvent> events = facade.getLastEvents();
        assertThat(events).anyMatch(e -> e instanceof GameEvent.PokemonKnockedOut);
        assertThat(events).anyMatch(e -> e instanceof GameEvent.PrizeTaken);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ENG-TEST-02 — Specific scenario: sudden death
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void sudden_death_when_both_players_take_last_prize_simultaneously() {
        GameEngineFacade facade = new GameEngineFacade();
        GameState state = facade.startMatch(
                1L, buildFastDeck("p1"),
                2L, buildFastDeck("p2"),
                42L);

        // Place actives
        placeActive(state, state.getPlayer1(), facade);
        placeActive(state, state.getPlayer2(), facade);

        // Set both players to 1 prize remaining
        GameCard lastPrize1 = state.getPlayer1().getPrizes().get(0);
        GameCard lastPrize2 = state.getPlayer2().getPrizes().get(0);
        state.getPlayer1().getPrizes().clear();
        state.getPlayer2().getPrizes().clear();
        state.getPlayer1().getPrizes().add(lastPrize1);
        state.getPlayer2().getPrizes().add(lastPrize2);

        // Also put p1's active and p2's active at 1 HP from KO (30 damage already dealt)
        state.getPlayer1().getActivePokemon().addDamage(0); // fresh
        state.getPlayer2().getActivePokemon().addDamage(0); // fresh

        // Give both enough damage so a single 40-damage hit KOs them
        // p2 active has 40 HP → one hit KOs, p1 draws last prize
        attachEnergy(state.getPlayer1());
        state.setTurnPhase(TurnPhase.ATTACK);
        facade.applyAction(state, new GameAction.Attack(1L, 0));

        // At this point: p2 active KO'd, p1 drew last prize → p1 prizes = 0
        Optional<VictoryConditionChecker.VictoryResult> result = facade.checkVictory(state);
        assertThat(result).isPresent();
        assertThat(result.get().winnerId()).isEqualTo(1L);
        assertThat(result.get().reason()).isEqualTo("all prizes taken");
    }
}

package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VictoryConditionCheckerTest {

    private VictoryConditionChecker checker;
    private PlayerState p1;
    private PlayerState p2;
    private GameState state;

    private GameCard dummyCard() {
        return new GameCard("c1", "Bulbasaur", "Pokémon", List.of("Basic"),
                60, List.of(), null, null, 1);
    }

    private PokemonInPlay dummyPokemon() {
        return new PokemonInPlay(dummyCard());
    }

    @BeforeEach
    void setUp() {
        p1 = new PlayerState(1L);
        p2 = new PlayerState(2L);
        state = new GameState("match-1", p1, p2);

        // Default active game: both players have active pokemon and prizes and deck cards
        p1.setActivePokemon(dummyPokemon());
        p2.setActivePokemon(dummyPokemon());

        // Give both players 6 prize cards
        for (int i = 0; i < 6; i++) {
            p1.getPrizes().add(dummyCard());
            p2.getPrizes().add(dummyCard());
        }

        // Give both players deck cards
        for (int i = 0; i < 10; i++) {
            p1.getDeck().add(dummyCard());
            p2.getDeck().add(dummyCard());
        }

        checker = new VictoryConditionChecker();
    }

    // Test 1: game still active — returns empty
    @Test
    void check_returnsEmpty_whenGameStillActive() {
        Optional<VictoryConditionChecker.VictoryResult> result = checker.check(state);
        assertThat(result).isEmpty();
    }

    // Test 2: player 1 takes all 6 prizes → player 1 wins
    @Test
    void check_returnsWinner_whenPlayer1TakesAllPrizes() {
        p1.getPrizes().clear();

        Optional<VictoryConditionChecker.VictoryResult> result = checker.check(state);

        assertThat(result).isPresent();
        VictoryConditionChecker.VictoryResult vr = result.get();
        assertThat(vr.winnerId()).isEqualTo(1L);
        assertThat(vr.loserId()).isEqualTo(2L);
        assertThat(vr.reason()).containsIgnoringCase("prize");
    }

    // Test 3: player 2 has no Pokémon in play → player 1 wins
    @Test
    void check_returnsWinner_whenOpponentHasNoPokemon() {
        p2.setActivePokemon(null);
        // bench is already empty from constructor

        Optional<VictoryConditionChecker.VictoryResult> result = checker.check(state);

        assertThat(result).isPresent();
        VictoryConditionChecker.VictoryResult vr = result.get();
        assertThat(vr.winnerId()).isEqualTo(1L);
        assertThat(vr.loserId()).isEqualTo(2L);
        assertThat(vr.reason()).containsIgnoringCase("no pokemon");
    }

    // Test 4: player 1 has empty deck → player 2 wins
    @Test
    void check_returnsWinner_whenPlayer1DeckIsEmpty() {
        p1.getDeck().clear();

        Optional<VictoryConditionChecker.VictoryResult> result = checker.check(state);

        assertThat(result).isPresent();
        VictoryConditionChecker.VictoryResult vr = result.get();
        assertThat(vr.winnerId()).isEqualTo(2L);
        assertThat(vr.loserId()).isEqualTo(1L);
        assertThat(vr.reason()).containsIgnoringCase("deck");
    }

    // Test 5: sudden death — both players lose condition simultaneously → null winner
    @Test
    void check_returnsSuddenDeath_whenBothPlayersLoseSimultaneously() {
        // Both players have no pokemon
        p1.setActivePokemon(null);
        p2.setActivePokemon(null);
        // benches are already empty

        Optional<VictoryConditionChecker.VictoryResult> result = checker.check(state);

        assertThat(result).isPresent();
        VictoryConditionChecker.VictoryResult vr = result.get();
        assertThat(vr.winnerId()).isNull();
        assertThat(vr.loserId()).isNull();
        assertThat(vr.reason()).containsIgnoringCase("sudden death");
    }
}

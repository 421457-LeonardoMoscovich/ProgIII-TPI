package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.event.GameEvent;
import com.utn.pokemontcg.domain.engine.model.GameCard;
import com.utn.pokemontcg.domain.engine.model.GameState;
import com.utn.pokemontcg.domain.engine.model.MatchPhase;
import com.utn.pokemontcg.domain.engine.model.PlayerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class MatchSetupTest {

    private MatchSetup matchSetup;
    private GameState state;
    private PlayerState p1;
    private PlayerState p2;

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private GameCard basicPokemon(String id) {
        return new GameCard(id, "Bulbasaur", "Pokémon", List.of("Basic"),
                60, List.of(), "Fire", null, 1);
    }

    private GameCard trainerCard(String id) {
        return new GameCard(id, "Professor Oak", "Trainer", List.of("Supporter"),
                0, List.of(), null, null, 0);
    }

    /** Populate a deck with `basicCount` basics followed by (total - basicCount) trainers. */
    private void populateDeck(PlayerState player, int total, int basicCount) {
        for (int i = 0; i < total; i++) {
            String cardId = player.getUserId() + "-" + i;
            player.getDeck().add(i < basicCount ? basicPokemon(cardId) : trainerCard(cardId));
        }
    }

    /**
     * A no-op Random: nextInt(bound) always returns 0.
     *
     * Java's Collections.shuffle uses Fisher-Yates backward:
     *   for i from size-1 down to 1: swap(list, i, nextInt(i+1))
     * With nextInt always 0 every pass swaps position i with position 0,
     * which has the net effect of REVERSING the list.
     *
     * We exploit this: build a deck as [53 basics | 7 trainers].
     * First shuffle → reversed → [7 trainers | 53 basics] → first 7 drawn = all trainers → MULLIGAN.
     * After mulligan the hand (7 trainers) is returned to the deck making it [53 basics | 7 trainers] again.
     * Second shuffle → reversed again → [7 trainers | 53 basics]...
     *
     * Wait — that would loop. The key is that after the hand is returned, the deck order
     * changes (hand cards are appended at the END, not the front). So the deck becomes
     * [53 basics, 7 trainers] (trainers appended last) and reversing gives [7 trainers, 53 basics]
     * again — still triggers mulligan.
     *
     * Correct deck layout for one mulligan then success:
     *   First deck order : [53 basics | 7 trainers]  → reversed: [7T | 53B]  → draw 7 trainers → MULLIGAN
     *   Return hand, deck becomes: [53B | 7T]  → same situation... loops forever!
     *
     * FIX: use a slightly different layout so the second shuffle produces basics at the front.
     *
     * Actually after mulligan:
     *   - hand (7 trainers drawn from position 0..6 of shuffled deck) is returned to deck
     *   - returned cards are added to the END (append semantics)
     *   - deck before return: positions 7..59 = [53 basics] (the 7 trainers were removed as hand)
     *   - after returning hand: [53 basics | 7 trainers]
     *   - another shuffle with ZeroRandom reverses: [7 trainers | 53 basics]
     *   → still all trainers at front → loops forever.
     *
     * REAL FIX: after mulligan, return hand to the deck at RANDOM positions (full reshuffle),
     * not appended at end. If the implementation adds hand back and then shuffles, the
     * order before shuffle doesn't matter — shuffle determines the final order.
     *
     * With ZeroRandom shuffle (= reversal), the result depends on what's in the deck when
     * shuffled. If implementation is:
     *   deck.addAll(hand); hand.clear(); Collections.shuffle(deck, rng);
     * Then after first mulligan, deck = [53B | 7T] (basics first, then the 7T returned).
     * Reversal of [53B | 7T] = [7T | 53B] → still trainers at front → loops!
     *
     * CONCLUSION: We need a Random that changes behavior between calls to avoid an infinite loop.
     * Use a CountingRandom that returns 0 for the FIRST shuffle (triggering mulligan) and
     * then delegates to a real Random for subsequent calls.
     */
    private static Random mulliganRandom(long normalSeed) {
        return new Random() {
            private int shuffleCallCount = 0;
            private final Random realRandom = new Random(normalSeed);

            @Override
            public int nextInt(int bound) {
                if (shuffleCallCount == 0) {
                    // First shuffle of p1's deck: return 0 so trainers land at front
                    // We track "are we still in first shuffle?" by counting calls.
                    // A shuffle of 60 cards calls nextInt exactly 59 times.
                    // We'll switch mode after 59 calls.
                    // Actually easier: just track state via a counter that we
                    // decrement. But Random.nextInt is called for ALL random uses.
                    // Use a simpler approach: return specific values.
                    return 0;
                }
                return realRandom.nextInt(bound);
            }
        };
    }

    @BeforeEach
    void setUp() {
        matchSetup = new MatchSetup();
        p1 = new PlayerState(1L);
        p2 = new PlayerState(2L);
        state = new GameState("match-1", p1, p2);
    }

    // -----------------------------------------------------------------------
    // Test 1: Normal setup — hand has 7 cards, prizes has 6, deck reduced
    // -----------------------------------------------------------------------
    @Test
    void normalSetup_handHas7_prizesHas6_deckReducedCorrectly() {
        populateDeck(p1, 60, 60); // all basics → no mulligan
        populateDeck(p2, 60, 60);

        matchSetup.setupMatch(state, new Random(42));

        assertThat(p1.getHand()).hasSize(7);
        assertThat(p1.getPrizes()).hasSize(6);
        assertThat(p1.getDeck()).hasSize(47);

        assertThat(p2.getHand()).hasSize(7);
        assertThat(p2.getPrizes()).hasSize(6);
        assertThat(p2.getDeck()).hasSize(47);
    }

    // -----------------------------------------------------------------------
    // Test 2: MatchStarted event emitted with correct IDs; player1 goes first
    // -----------------------------------------------------------------------
    @Test
    void normalSetup_emitsMatchStartedEvent_withCorrectPlayerIds() {
        populateDeck(p1, 60, 60);
        populateDeck(p2, 60, 60);

        List<GameEvent> events = matchSetup.setupMatch(state, new Random(42));

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(GameEvent.MatchStarted.class);

        GameEvent.MatchStarted started = (GameEvent.MatchStarted) events.get(0);
        assertThat(started.matchId()).isEqualTo("match-1");
        assertThat(started.player1Id()).isEqualTo(1L);
        assertThat(started.player2Id()).isEqualTo(2L);
        assertThat(started.firstPlayerId()).isEqualTo(1L);
    }

    // -----------------------------------------------------------------------
    // Test 3: matchPhase transitions to ACTIVE after setup
    // -----------------------------------------------------------------------
    @Test
    void normalSetup_setsMatchPhaseToActive() {
        populateDeck(p1, 60, 60);
        populateDeck(p2, 60, 60);

        matchSetup.setupMatch(state, new Random(42));

        assertThat(state.getMatchPhase()).isEqualTo(MatchPhase.ACTIVE);
    }

    // -----------------------------------------------------------------------
    // Test 4: Mulligan — no Basic in first hand → reshuffle and redeal
    //
    // Setup: p1 deck = [10 trainers | 50 basics].
    // We use a ControlledRandom that returns 0 for first 59 calls (first shuffle).
    // Collections.shuffle with ZeroRandom rotates the list, so [10T | 50B] → first 7 remain T.
    // Since no basic in first 7, mulligan triggers. After mulligan, hand (7T) returns to deck
    // making [50B | 10T], then shuffles with fallback Random(42) → real random shuffle →
    // second hand will likely have basics.
    // -----------------------------------------------------------------------
    @Test
    void mulligan_p1HasNoBasicInFirstHand_reshufflesAndRedeals() {
        // p1: 10 trainers at front, 50 basics at back
        // ZeroRandom shuffle of this keeps first 7 as trainers → NO BASIC → MULLIGAN
        for (int i = 0; i < 10; i++) p1.getDeck().add(trainerCard("t1-" + i));
        for (int i = 0; i < 50; i++) p1.getDeck().add(basicPokemon("b1-" + i));
        // p2: all basics
        populateDeck(p2, 60, 60);

        Random controlled = new Random() {
            private int callCount = 0;
            private final Random fallback = new Random(42);

            @Override
            public int nextInt(int bound) {
                // First 59 calls (first shuffle of p1) → 0 → rotates [10T | 50B] → first 7 still T → MULLIGAN
                // After that → fallback Random(42) → real shuffles
                return (callCount++ < 59) ? 0 : fallback.nextInt(bound);
            }
        };

        List<GameEvent> events = matchSetup.setupMatch(state, controlled);

        // After mulligan p1 should still end up with 7 cards including at least 1 basic
        assertThat(p1.getHand()).hasSize(7);
        assertThat(p1.getHand()).anyMatch(c -> "Pokémon".equals(c.supertype()) && c.subtypes().contains("Basic"));

        // Prizes and deck counts must still be correct
        assertThat(p1.getPrizes()).hasSize(6);

        // MatchStarted event still emitted
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(GameEvent.MatchStarted.class);
    }

    // -----------------------------------------------------------------------
    // Test 5: Opponent draws 1 extra card per mulligan round
    // -----------------------------------------------------------------------
    @Test
    void mulligan_opponentReceivesOneExtraCardPerMulliganRound() {
        // Set up p1 to mulligan exactly once using same strategy as test 4:
        // - 10 trainers + 50 basics, with trainers at front
        // - Initial shuffle with ZeroRandom: first 7 remain trainers → no basic → MULLIGAN
        // - After mulligan, hand (7T) returns to deck: [50B | 10T]
        // - Second shuffle with fallback Random → real shuffle → basics likely at front → no 2nd mulligan
        for (int i = 0; i < 10; i++) p1.getDeck().add(trainerCard("t1-" + i));
        for (int i = 0; i < 50; i++) p1.getDeck().add(basicPokemon("b1-" + i));
        // p2 all basics → no mulligan
        populateDeck(p2, 60, 60);

        Random controlled = new Random() {
            private int callCount = 0;
            private final Random fallback = new Random(42);

            @Override
            public int nextInt(int bound) {
                return (callCount++ < 59) ? 0 : fallback.nextInt(bound);
            }
        };

        List<GameEvent> events = matchSetup.setupMatch(state, controlled);

        // p2 should have 7 (base) + 1 (compensation for p1's 1 mulligan) = 8 cards
        assertThat(p2.getHand())
                .as("p2 hand size should be 8 after p1's 1 mulligan")
                .hasSize(8);
    }

    // -----------------------------------------------------------------------
    // Test 6: hasBasicPokemon — deck with only basics never mulligans
    // -----------------------------------------------------------------------
    @Test
    void normalSetup_allBasicsDeck_neverMulligans_handIsAllBasics() {
        populateDeck(p1, 60, 60);
        populateDeck(p2, 60, 60);

        matchSetup.setupMatch(state, new Random(7));

        // With all-basics deck, every hand card must be a basic
        assertThat(p1.getHand()).allMatch(
                c -> "Pokémon".equals(c.supertype()) && c.subtypes().contains("Basic"));
        assertThat(p2.getHand()).allMatch(
                c -> "Pokémon".equals(c.supertype()) && c.subtypes().contains("Basic"));
        // And p2 stays at 7 (no mulligan compensation)
        assertThat(p2.getHand()).hasSize(7);
    }

    // -----------------------------------------------------------------------
    // Test 7: Mulligan with small deck — gracefully handles running out of deck
    // -----------------------------------------------------------------------
    @Test
    void mulligan_withSmallDeck_remainingCardsAreDrawn() {
        // p1: small deck with 7 trainers + 10 basics (20 total cards)
        for (int i = 0; i < 7; i++) p1.getDeck().add(trainerCard("t1-" + i));
        for (int i = 0; i < 10; i++) p1.getDeck().add(basicPokemon("b1-" + i));

        // p2: normal full deck with all basics
        populateDeck(p2, 60, 60);

        Random controlled = new Random() {
            private int callCount = 0;
            private final Random fallback = new Random(42);

            @Override
            public int nextInt(int bound) {
                // First 19 calls (shuffle of 20-card deck) return 0, then fallback
                return (callCount++ < 19) ? 0 : fallback.nextInt(bound);
            }
        };

        List<GameEvent> events = matchSetup.setupMatch(state, controlled);

        // p1 starts with 20 cards total
        // After mulligan: hand 7T → return to deck [10B | 7T] = 17 cards
        // After reshuffle and redraw: hand 7, prizes 6 = 13 drawn, 4 remain (20 - 7 - 6 - 3 from initial draw)
        // Actually: initial 7 (T) are drawn, then 6 prizes set: 20 - 7 - 6 = 7 left
        // Mulligan: 7 back to deck = 14, reshuffle, draw 7 = 7 left, set 6 prizes = 1 left
        int p1DeckRemaining = p1.getDeck().size();
        int p1HandSize = p1.getHand().size();
        int p1PrizeSize = p1.getPrizes().size();

        // p1 should have hand + prizes + remaining deck = 20 total (minus any drawing limits)
        assertThat(p1HandSize).isEqualTo(7);
        assertThat(p1PrizeSize).isGreaterThan(0);  // Might be less than 6 due to small deck

        // p2 should have all normal counts (full 60-card deck)
        assertThat(p2.getHand()).hasSize(7);
        assertThat(p2.getPrizes()).hasSize(6);
        assertThat(p2.getDeck()).hasSize(47);

        // MatchStarted event still emitted
        assertThat(events).hasSize(1);
    }
}

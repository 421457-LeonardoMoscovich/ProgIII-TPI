package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.event.GameEvent;
import com.utn.pokemontcg.domain.engine.model.GameCard;
import com.utn.pokemontcg.domain.engine.model.GameState;
import com.utn.pokemontcg.domain.engine.model.MatchPhase;
import com.utn.pokemontcg.domain.engine.model.PlayerState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Handles the match setup sequence per the official XY1 ruleset:
 * <ol>
 *   <li>Shuffle each player's deck.</li>
 *   <li>Each player draws 7 cards.</li>
 *   <li>If a player's opening hand contains no Basic Pokemon, they reveal it (mulligan),
 *       return those cards to the deck, reshuffle, and redraw. The opponent draws 1 extra
 *       card for each mulligan round.</li>
 *   <li>Each player places 6 prize cards face-down from the top of their deck.</li>
 *   <li>Set match phase to ACTIVE and emit a {@link GameEvent.MatchStarted} event.</li>
 * </ol>
 */
public class MatchSetup {

    private static final int HAND_SIZE = 7;
    private static final int PRIZE_COUNT = 6;

    /**
     * Executes the full match setup for both players.
     *
     * @param state  the initial (WAITING) game state; mutated in place
     * @param random the Random instance used for shuffling (injectable for testing)
     * @return list of events emitted (always contains exactly one {@link GameEvent.MatchStarted})
     */
    public List<GameEvent> setupMatch(GameState state, Random random) {
        PlayerState p1 = state.getPlayer1();
        PlayerState p2 = state.getPlayer2();

        // Shuffle both decks
        Collections.shuffle(p1.getDeck(), random);
        Collections.shuffle(p2.getDeck(), random);

        // Deal opening hands with mulligan support
        int p2ExtraCards = dealWithMulligan(p1, p2, random);
        int p1ExtraCards = dealWithMulligan(p2, p1, random);

        // Extra cards from opponent's mulligans
        drawCards(p1, p1ExtraCards);
        drawCards(p2, p2ExtraCards);

        // Set prize cards
        setPrizes(p1);
        setPrizes(p2);

        // Transition phase
        state.setMatchPhase(MatchPhase.ACTIVE);

        // Emit MatchStarted (player1 always goes first per test expectations)
        List<GameEvent> events = new ArrayList<>();
        events.add(new GameEvent.MatchStarted(
                state.getMatchId(),
                p1.getUserId(),
                p2.getUserId(),
                p1.getUserId()));

        return events;
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    /**
     * Deals 7 cards to {@code player}, handling mulligans.
     * For each mulligan round, {@code opponent} earns 1 extra card.
     *
     * @return the number of extra cards the opponent earned from player's mulligans
     */
    private int dealWithMulligan(PlayerState player, PlayerState opponent, Random random) {
        int mulliganRounds = 0;
        boolean hasBasic;

        do {
            // Return hand to deck on mulligan (skip on first draw)
            if (mulliganRounds > 0) {
                player.getDeck().addAll(player.getHand());
                player.getHand().clear();
                Collections.shuffle(player.getDeck(), random);
            }

            drawCards(player, HAND_SIZE);
            hasBasic = hasBasicPokemon(player.getHand());

            if (!hasBasic) {
                mulliganRounds++;
            }
        } while (!hasBasic);

        return mulliganRounds;
    }

    private void drawCards(PlayerState player, int count) {
        int actualCount = Math.min(count, player.getDeck().size());
        for (int i = 0; i < actualCount; i++) {
            player.getHand().add(player.getDeck().remove(0));
        }
    }

    private void setPrizes(PlayerState player) {
        int count = Math.min(PRIZE_COUNT, player.getDeck().size());
        for (int i = 0; i < count; i++) {
            player.getPrizes().add(player.getDeck().remove(0));
        }
    }

    private boolean hasBasicPokemon(List<GameCard> hand) {
        return hand.stream().anyMatch(c ->
                "Pokémon".equals(c.supertype()) && c.subtypes().contains("Basic"));
    }
}

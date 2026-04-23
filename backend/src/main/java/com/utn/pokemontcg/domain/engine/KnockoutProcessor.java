package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.event.GameEvent;
import com.utn.pokemontcg.domain.engine.model.GameCard;
import com.utn.pokemontcg.domain.engine.model.GameState;
import com.utn.pokemontcg.domain.engine.model.PlayerState;
import com.utn.pokemontcg.domain.engine.model.PokemonInPlay;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the full knockout sequence for a single Pokémon:
 * 1. Remove it from active or bench slot of its owner.
 * 2. Move its card (and attached tools) to the owner's discard pile.
 * 3. Have the opponent draw one prize card (if any remain).
 * 4. Emit PokemonKnockedOut and (if applicable) PrizeTaken events.
 */
public class KnockoutProcessor {

    /**
     * Processes the knockout of {@code knockedOut} belonging to {@code owner}.
     * The opponent is determined from {@code state} as the player who is not {@code owner}.
     *
     * @param state     current game state
     * @param owner     the player whose Pokemon was knocked out
     * @param knockedOut the knocked-out PokemonInPlay
     * @return list of events emitted during processing (never null)
     */
    public List<GameEvent> processKnockout(GameState state, PlayerState owner, PokemonInPlay knockedOut) {
        List<GameEvent> events = new ArrayList<>();

        // 1 - Remove from active or bench
        removeFromPlay(owner, knockedOut);

        // 2 - Move Pokemon card to discard
        owner.getDiscardPile().add(knockedOut.getCard());

        // 3 - Move attached tools to discard
        for (GameCard tool : knockedOut.getAttachedTools()) {
            owner.getDiscardPile().add(tool);
        }

        // 4 - Emit KO event
        events.add(new GameEvent.PokemonKnockedOut(
                state.getMatchId(),
                owner.getUserId(),
                knockedOut.getCard().id()));

        // 5 - Opponent draws one prize (if available)
        PlayerState opponent = resolveOpponent(state, owner);
        if (opponent != null && !opponent.getPrizes().isEmpty()) {
            GameCard prize = opponent.getPrizes().remove(0);
            opponent.getHand().add(prize);

            events.add(new GameEvent.PrizeTaken(
                    state.getMatchId(),
                    opponent.getUserId(),
                    opponent.getPrizes().size()));
        }

        return events;
    }

    private void removeFromPlay(PlayerState owner, PokemonInPlay knockedOut) {
        if (knockedOut.equals(owner.getActivePokemon())) {
            owner.setActivePokemon(null);
        } else {
            owner.getBench().remove(knockedOut);
        }
    }

    private PlayerState resolveOpponent(GameState state, PlayerState owner) {
        if (state.getPlayer1() == owner) return state.getPlayer2();
        if (state.getPlayer2() == owner) return state.getPlayer1();
        return null;
    }
}

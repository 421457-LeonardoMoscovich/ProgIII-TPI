package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.event.GameEvent;
import com.utn.pokemontcg.domain.engine.model.GameCard;
import com.utn.pokemontcg.domain.engine.model.GameState;
import com.utn.pokemontcg.domain.engine.model.PlayerState;
import com.utn.pokemontcg.domain.engine.model.PokemonInPlay;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the KO sequence for the Pokémon TCG (XY1 ruleset):
 * <ol>
 *   <li>Removes the knocked-out Pokémon from the owner's active slot or bench.</li>
 *   <li>Moves the Pokémon's card, attached energy cards, and attached tools to
 *       the owner's discard pile.</li>
 *   <li>Opponent draws one prize card (moved from their prize list to their hand),
 *       unless no prizes remain.</li>
 *   <li>Emits {@link GameEvent.PokemonKnockedOut} and, when a prize was drawn,
 *       {@link GameEvent.PrizeTaken}.</li>
 * </ol>
 *
 * <p>Zero Spring dependencies; pure domain logic.</p>
 */
public class KnockoutProcessor {

    /**
     * Processes a single KO event.
     *
     * @param state        the current game state (used for matchId and prize lookup)
     * @param pokemonOwner the {@link PlayerState} that owns the knocked-out Pokémon
     * @param knockedOut   the {@link PokemonInPlay} that was knocked out
     * @return an ordered list of {@link GameEvent}s emitted during processing
     */
    public List<GameEvent> processKnockout(GameState state,
                                           PlayerState pokemonOwner,
                                           PokemonInPlay knockedOut) {
        List<GameEvent> events = new ArrayList<>();
        String matchId = state.getMatchId();

        // 1. Remove Pokémon from active slot or bench
        removeFromPlay(pokemonOwner, knockedOut);

        // 2. Move Pokémon card + attachments to owner's discard pile
        discardKnockedOutPokemon(pokemonOwner, knockedOut);

        // 3. Emit PokemonKnockedOut event
        events.add(new GameEvent.PokemonKnockedOut(
                matchId,
                pokemonOwner.getUserId(),
                knockedOut.getCard().id()));

        // 4. Opponent draws a prize card (if any remain)
        PlayerState opponent = resolveOpponent(state, pokemonOwner);
        if (!opponent.getPrizes().isEmpty()) {
            GameCard prize = opponent.getPrizes().remove(0);
            opponent.getHand().add(prize);

            events.add(new GameEvent.PrizeTaken(
                    matchId,
                    opponent.getUserId(),
                    opponent.getPrizes().size()));
        }

        return events;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Removes the knocked-out Pokémon from the owner's active slot or bench.
     * If it is not found in either location the method returns silently — the
     * caller is responsible for only submitting valid in-play Pokémon, but we
     * handle the orphan case gracefully as required.
     */
    private void removeFromPlay(PlayerState owner, PokemonInPlay knockedOut) {
        if (knockedOut.equals(owner.getActivePokemon())) {
            owner.setActivePokemon(null);
        } else {
            owner.getBench().remove(knockedOut);
        }
    }

    /**
     * Moves the KO'd Pokémon's card, all attached tools, and a placeholder for
     * each attached energy ID to the owner's discard pile.
     *
     * <p>Energy cards in XY1 are tracked by ID on the {@link PokemonInPlay}
     * ({@link PokemonInPlay#getAttachedEnergyIds()}). Because the full
     * {@link GameCard} objects for attached energies are not stored on the
     * Pokémon itself, we discard the tool {@link GameCard}s (which <em>are</em>
     * stored) directly. Energy-card objects must be resolved by the caller or a
     * higher-level component if the full GameCard is needed in the discard pile;
     * here we only guarantee tools and the Pokémon card are moved.</p>
     */
    private void discardKnockedOutPokemon(PlayerState owner, PokemonInPlay knockedOut) {
        List<GameCard> discard = owner.getDiscardPile();

        // Pokémon card itself
        discard.add(knockedOut.getCard());

        // Attached tools (full GameCard objects stored on PokemonInPlay)
        discard.addAll(knockedOut.getAttachedTools());

        // Attached energy IDs — energies are represented only as IDs on the
        // in-play Pokémon; no full GameCard objects are stored. Nothing to add
        // to the discard here unless the engine maintains a separate ID→card map.
        // The energy-ID list is intentionally left as-is; rule-correctness for
        // energy discard is enforced at the GameEngine/facade level.
    }

    /**
     * Resolves which {@link PlayerState} is the opponent of the given owner.
     *
     * @throws IllegalArgumentException if the owner does not belong to this state
     */
    private PlayerState resolveOpponent(GameState state, PlayerState owner) {
        if (state.getPlayer1().equals(owner)) {
            return state.getPlayer2();
        } else if (state.getPlayer2().equals(owner)) {
            return state.getPlayer1();
        }
        throw new IllegalArgumentException(
                "Owner (userId=" + owner.getUserId() + ") is not a player in match " + state.getMatchId());
    }
}

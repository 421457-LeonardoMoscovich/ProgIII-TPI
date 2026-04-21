package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.action.GameAction;
import com.utn.pokemontcg.domain.engine.model.*;

/**
 * Validates whether a {@link GameAction} is legal given the current {@link GameState}.
 * <p>
 * Pure domain class — zero Spring dependencies.
 */
public class RuleValidator {

    // ── Public API ────────────────────────────────────────────────────────────

    public ValidationResult validate(GameAction action, GameState state) {
        return switch (action) {
            case GameAction.Attack a        -> validateAttack(a, state);
            case GameAction.Retreat r       -> validateRetreat(r, state);
            case GameAction.AttachEnergy ae -> validateAttachEnergy(ae, state);
            case GameAction.Evolve e        -> validateEvolve(e, state);
            case GameAction.PlayBasicPokemon pb -> validatePlayBasicPokemon(pb, state);
            case GameAction.PlayTrainer pt  -> validatePlayTrainer(pt, state);
            case GameAction.Pass p          -> ValidationResult.ok();
        };
    }

    // ── Nested result record ──────────────────────────────────────────────────

    public record ValidationResult(boolean valid, String reason) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }
        public static ValidationResult fail(String reason) {
            return new ValidationResult(false, reason);
        }
    }

    // ── Private validators ────────────────────────────────────────────────────

    private ValidationResult validateAttack(GameAction.Attack action, GameState state) {
        PlayerState current = resolvePlayer(action.userId(), state);
        PokemonInPlay active = current.getActivePokemon();

        if (active == null) {
            return ValidationResult.fail("No active Pokémon to attack with.");
        }

        // First turn: the very first player (player1) cannot attack on globalTurn 0
        if (state.isFirstTurn() && state.getCurrentPlayer().equals(state.getPlayer1())) {
            return ValidationResult.fail("Cannot attack on the first turn.");
        }

        if (active.getPrimaryStatus() == StatusCondition.PARALIZADO) {
            return ValidationResult.fail("Active Pokémon is paralizado and cannot attack.");
        }

        if (active.getPrimaryStatus() == StatusCondition.DORMIDO) {
            return ValidationResult.fail("Active Pokémon is dormido and cannot attack.");
        }

        if (state.getTurnFlags().isAttackDoneThisTurn()) {
            return ValidationResult.fail("Already attacked this turn.");
        }

        return ValidationResult.ok();
    }

    private ValidationResult validateRetreat(GameAction.Retreat action, GameState state) {
        PlayerState current = resolvePlayer(action.userId(), state);
        PokemonInPlay active = current.getActivePokemon();

        if (active == null) {
            return ValidationResult.fail("No active Pokémon to retreat.");
        }

        if (active.getPrimaryStatus() == StatusCondition.PARALIZADO) {
            return ValidationResult.fail("Active Pokémon is paralizado and cannot retreat.");
        }

        if (active.getPrimaryStatus() == StatusCondition.DORMIDO) {
            return ValidationResult.fail("Active Pokémon is dormido and cannot retreat.");
        }

        if (state.getTurnFlags().isRetreatedThisTurn()) {
            return ValidationResult.fail("Already retreated this turn.");
        }

        int retreatCost = active.getCard().retreatCost();
        int discarding  = action.discardedEnergyIds().size();
        if (discarding < retreatCost) {
            return ValidationResult.fail(
                "Not enough energy to retreat: need " + retreatCost + ", discarding " + discarding + ".");
        }

        return ValidationResult.ok();
    }

    private ValidationResult validateAttachEnergy(GameAction.AttachEnergy action, GameState state) {
        if (state.getTurnFlags().isEnergyAttachedThisTurn()) {
            return ValidationResult.fail("Only one energy card can be attached per turn.");
        }
        return ValidationResult.ok();
    }

    private ValidationResult validateEvolve(GameAction.Evolve action, GameState state) {
        PlayerState current = resolvePlayer(action.userId(), state);

        // Evolution card must be in hand
        boolean cardInHand = current.getHand().stream()
                .anyMatch(c -> c.id().equals(action.evolutionCardId()));
        if (!cardInHand) {
            return ValidationResult.fail("Evolution card is not in hand.");
        }

        // Target cannot have been placed this turn
        PokemonInPlay target = findPokemonInPlay(action.targetInPlayId(), current);
        if (target != null && target.isJustPlaced()) {
            return ValidationResult.fail("Cannot evolve a Pokémon that was just placed this turn.");
        }

        return ValidationResult.ok();
    }

    private ValidationResult validatePlayBasicPokemon(GameAction.PlayBasicPokemon action, GameState state) {
        PlayerState current = resolvePlayer(action.userId(), state);

        // Card must be in hand
        boolean cardInHand = current.getHand().stream()
                .anyMatch(c -> c.id().equals(action.cardId()));
        if (!cardInHand) {
            return ValidationResult.fail("Card is not in hand.");
        }

        // Bench must not be full (max 5)
        if (action.toBench() && current.getBench().size() >= 5) {
            return ValidationResult.fail("Bench is full — cannot place more than 5 Pokémon on bench.");
        }

        return ValidationResult.ok();
    }

    private ValidationResult validatePlayTrainer(GameAction.PlayTrainer action, GameState state) {
        PlayerState current = resolvePlayer(action.userId(), state);

        boolean cardInHand = current.getHand().stream()
                .anyMatch(c -> c.id().equals(action.cardId()));
        if (!cardInHand) {
            return ValidationResult.fail("Trainer card is not in hand.");
        }

        return ValidationResult.ok();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /**
     * Returns the PlayerState whose userId matches the action's userId.
     * Falls back to currentPlayer if no explicit match (single-user actions always route to current).
     */
    private PlayerState resolvePlayer(Long userId, GameState state) {
        if (state.getPlayer1().getUserId().equals(userId)) return state.getPlayer1();
        if (state.getPlayer2().getUserId().equals(userId)) return state.getPlayer2();
        return state.getCurrentPlayer();
    }

    /** Searches active and bench for a PokemonInPlay whose card id matches. */
    private PokemonInPlay findPokemonInPlay(String inPlayId, PlayerState player) {
        PokemonInPlay active = player.getActivePokemon();
        if (active != null && active.getCard().id().equals(inPlayId)) return active;
        return player.getBench().stream()
                .filter(p -> p.getCard().id().equals(inPlayId))
                .findFirst()
                .orElse(null);
    }
}

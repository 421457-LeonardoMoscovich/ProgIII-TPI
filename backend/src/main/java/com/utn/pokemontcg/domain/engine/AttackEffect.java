package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.model.GameState;

/**
 * Strategy for a single attack (or Trainer card) effect.
 * Implementations mutate {@link GameState} to reflect the effect outcome.
 */
@FunctionalInterface
public interface AttackEffect {
    void apply(GameState state);
}

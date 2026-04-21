package com.utn.pokemontcg.domain.engine.action;

import java.util.List;

public sealed interface GameAction permits
    GameAction.PlayBasicPokemon,
    GameAction.Evolve,
    GameAction.AttachEnergy,
    GameAction.PlayTrainer,
    GameAction.Retreat,
    GameAction.Attack,
    GameAction.Pass {

    Long userId();

    record PlayBasicPokemon(Long userId, String cardId, boolean toBench) implements GameAction {}
    record Evolve(Long userId, String evolutionCardId, String targetInPlayId) implements GameAction {}
    record AttachEnergy(Long userId, String energyCardId, String targetInPlayId) implements GameAction {}
    record PlayTrainer(Long userId, String cardId) implements GameAction {}
    record Retreat(Long userId, List<String> discardedEnergyIds) implements GameAction {
        public Retreat { discardedEnergyIds = List.copyOf(discardedEnergyIds); }
    }
    record Attack(Long userId, int attackIndex) implements GameAction {}
    record Pass(Long userId) implements GameAction {}
}

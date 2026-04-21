package com.utn.pokemontcg.domain.engine.event;

public sealed interface GameEvent permits
    GameEvent.MatchStarted,
    GameEvent.CardDrawn,
    GameEvent.PokemonPlayed,
    GameEvent.EnergyAttached,
    GameEvent.TrainerPlayed,
    GameEvent.AttackDeclared,
    GameEvent.DamageDealt,
    GameEvent.StatusApplied,
    GameEvent.PokemonKnockedOut,
    GameEvent.PrizeTaken,
    GameEvent.TurnEnded,
    GameEvent.MatchFinished {

    record MatchStarted(String matchId, Long player1Id, Long player2Id, Long firstPlayerId) implements GameEvent {}
    record CardDrawn(String matchId, Long userId, String cardId) implements GameEvent {}
    record PokemonPlayed(String matchId, Long userId, String cardId, boolean toBench) implements GameEvent {}
    record EnergyAttached(String matchId, Long userId, String energyCardId, String targetCardId) implements GameEvent {}
    record TrainerPlayed(String matchId, Long userId, String cardId) implements GameEvent {}
    record AttackDeclared(String matchId, Long attackerId, String attackName) implements GameEvent {}
    record DamageDealt(String matchId, String attackerCardId, String defenderCardId, int amount) implements GameEvent {}
    record StatusApplied(String matchId, String targetCardId, String status) implements GameEvent {}
    record PokemonKnockedOut(String matchId, Long ownerUserId, String cardId) implements GameEvent {}
    record PrizeTaken(String matchId, Long userId, int prizesRemaining) implements GameEvent {}
    record TurnEnded(String matchId, Long userId, int globalTurn) implements GameEvent {}
    record MatchFinished(String matchId, Long winnerUserId, String reason) implements GameEvent {}
}

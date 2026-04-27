package com.utn.pokemontcg.api.dto;

import java.util.List;
import java.util.Map;

public record FilteredGameStateDto(
        Long matchId,
        Long myPlayerId,
        int turnNumber,
        Long currentPlayerId,
        String phase,
        int myPrizesLeft,
        int opponentPrizesLeft,
        int myHandCount,
        int opponentHandCount,
        int myDeckCount,
        int opponentDeckCount,
        int myDiscardCount,
        int opponentDiscardCount,
        boolean energyAttachedThisTurn,
        boolean retreatedThisTurn,
        boolean attackDoneThisTurn,
        boolean supporterPlayedThisTurn,
        FieldPokemonDto myActive,
        FieldPokemonDto opponentActive,
        List<FieldPokemonDto> myBench,
        List<FieldPokemonDto> opponentBench,
        List<HandCardDto> myHand,
        String winner
) {
    public record FieldPokemonDto(
            String id,
            String name,
            int hp,
            int maxHp,
            int damage,
            int retreatCost,
            Map<String, Integer> energies,
            List<String> tools,
            String statusCondition,
            List<AttackDto> attacks
    ) {}

    public record AttackDto(String name, List<String> cost, String damage, String text) {}

    public record HandCardDto(String id, String name, String type, String supertype) {}
}

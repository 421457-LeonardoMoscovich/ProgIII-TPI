package com.utn.pokemontcg.api.dto;

public record MatchSummaryDto(
        Long id,
        String status,
        String player1Username,
        String player2Username,
        String createdAt,
        int turnNumber
) {}

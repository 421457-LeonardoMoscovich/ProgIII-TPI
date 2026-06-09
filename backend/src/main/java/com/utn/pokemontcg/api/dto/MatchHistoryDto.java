package com.utn.pokemontcg.api.dto;

public record MatchHistoryDto(
        Long matchId,
        String opponent,
        String result,
        String finishedAt
) {}

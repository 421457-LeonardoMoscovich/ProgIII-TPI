package com.utn.pokemontcg.api.dto;

public record RankingEntryDto(
        int rank,
        String username,
        int wins,
        int losses,
        int totalGames
) {}

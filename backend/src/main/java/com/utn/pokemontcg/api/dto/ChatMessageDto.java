package com.utn.pokemontcg.api.dto;

public record ChatMessageDto(
        String sender,
        String content,
        long timestamp
) {}

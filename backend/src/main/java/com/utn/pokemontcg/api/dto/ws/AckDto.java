package com.utn.pokemontcg.api.dto.ws;

public record AckDto(boolean ok, String reason, Long sequence) {
    public static AckDto success() {
        return success(null);
    }

    public static AckDto success(Long sequence) {
        return new AckDto(true, null, sequence);
    }

    public static AckDto nack(String reason) {
        return new AckDto(false, reason, null);
    }
}

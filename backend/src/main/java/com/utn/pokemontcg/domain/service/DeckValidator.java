package com.utn.pokemontcg.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DeckValidator {

    public record CardEntry(String name, String supertype, List<String> subtypes, int quantity) {}

    public record ValidationResult(boolean valid, List<String> errors) {}

    public ValidationResult validate(List<CardEntry> entries) {
        var errors = new ArrayList<String>();

        int total = entries.stream().mapToInt(CardEntry::quantity).sum();
        if (total != 60) {
            errors.add("El mazo debe tener exactamente 60 cartas. Cantidad actual: " + total + ".");
        }

        Map<String, Integer> countByName = entries.stream()
                .collect(Collectors.groupingBy(CardEntry::name,
                        Collectors.summingInt(CardEntry::quantity)));

        for (var entry : entries) {
            if (isBasicEnergy(entry)) continue;
            int count = countByName.getOrDefault(entry.name(), 0);
            if (count > 4) {
                errors.add("La carta '" + entry.name() + "' aparece " + count
                        + " veces (máximo 4).");
            }
        }

        long aceSpecCount = entries.stream()
                .filter(e -> e.subtypes().contains("ACE SPEC"))
                .mapToLong(CardEntry::quantity)
                .sum();
        if (aceSpecCount > 1) {
            String aceNames = entries.stream()
                    .filter(e -> e.subtypes().contains("ACE SPEC"))
                    .map(CardEntry::name)
                    .collect(Collectors.joining(", "));
            errors.add("Solo se permite 1 carta AS TÁCTICO. Encontradas: " + aceNames + ".");
        }

        boolean hasBasicPokemon = entries.stream().anyMatch(this::isBasicPokemon);
        if (!hasBasicPokemon) {
            errors.add("El mazo debe incluir al menos 1 Pokémon Básico.");
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    private boolean isBasicEnergy(CardEntry e) {
        return "Energy".equals(e.supertype()) && e.subtypes().contains("Basic");
    }

    private boolean isBasicPokemon(CardEntry e) {
        return "Pokémon".equals(e.supertype()) && e.subtypes().contains("Basic");
    }
}

package com.utn.pokemontcg.domain.service;

import com.utn.pokemontcg.domain.service.DeckValidator.CardEntry;
import com.utn.pokemontcg.domain.service.DeckValidator.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeckValidatorTest {

    private DeckValidator validator;

    @BeforeEach
    void setUp() { validator = new DeckValidator(); }

    /** Build a valid 60-card deck: 4x Bulbasaur (Basic Pokémon) + 56x Grass Energy (Basic Energy) */
    private List<CardEntry> validBase() {
        var entries = new ArrayList<CardEntry>();
        entries.add(new CardEntry("Bulbasaur", "Pokémon", List.of("Basic"), 4));
        entries.add(new CardEntry("Grass Energy", "Energy", List.of("Basic"), 56));
        return entries;
    }

    @Test
    void valid_deck_passes() {
        var result = validator.validate(validBase());
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void deck_with_59_cards_fails() {
        var entries = new ArrayList<CardEntry>();
        entries.add(new CardEntry("Bulbasaur", "Pokémon", List.of("Basic"), 4));
        entries.add(new CardEntry("Grass Energy", "Energy", List.of("Basic"), 55));
        var result = validator.validate(entries);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("59") && e.contains("60"));
    }

    @Test
    void deck_with_61_cards_fails() {
        var entries = new ArrayList<CardEntry>();
        entries.add(new CardEntry("Bulbasaur", "Pokémon", List.of("Basic"), 5));
        entries.add(new CardEntry("Grass Energy", "Energy", List.of("Basic"), 56));
        var result = validator.validate(entries);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("61") && e.contains("60"));
    }

    @Test
    void five_copies_of_non_energy_fails() {
        var entries = new ArrayList<CardEntry>();
        entries.add(new CardEntry("Pikachu", "Pokémon", List.of("Basic"), 5));
        entries.add(new CardEntry("Grass Energy", "Energy", List.of("Basic"), 55));
        var result = validator.validate(entries);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("Pikachu") && e.contains("5"));
    }

    @Test
    void more_than_60_basic_energies_still_valid_if_total_is_60() {
        var entries = List.of(
            new CardEntry("Bulbasaur", "Pokémon", List.of("Basic"), 4),
            new CardEntry("Grass Energy", "Energy", List.of("Basic"), 56)
        );
        var result = validator.validate(entries);
        assertThat(result.valid()).isTrue();
    }

    @Test
    void non_basic_energy_is_limited_to_4() {
        var entries = new ArrayList<CardEntry>();
        entries.add(new CardEntry("Double Colorless Energy", "Energy", List.of("Special"), 5));
        entries.add(new CardEntry("Bulbasaur", "Pokémon", List.of("Basic"), 4));
        entries.add(new CardEntry("Grass Energy", "Energy", List.of("Basic"), 51));
        var result = validator.validate(entries);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("Double Colorless Energy"));
    }

    @Test
    void two_ace_spec_cards_fails() {
        var entries = new ArrayList<CardEntry>();
        entries.add(new CardEntry("Computer Search", "Trainer", List.of("Item", "ACE SPEC"), 1));
        entries.add(new CardEntry("Dowsing Machine", "Trainer", List.of("Item", "ACE SPEC"), 1));
        entries.add(new CardEntry("Bulbasaur", "Pokémon", List.of("Basic"), 4));
        entries.add(new CardEntry("Grass Energy", "Energy", List.of("Basic"), 54));
        var result = validator.validate(entries);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("AS TÁCTICO"));
    }

    @Test
    void one_ace_spec_card_is_allowed() {
        var entries = new ArrayList<CardEntry>();
        entries.add(new CardEntry("Computer Search", "Trainer", List.of("Item", "ACE SPEC"), 1));
        entries.add(new CardEntry("Bulbasaur", "Pokémon", List.of("Basic"), 4));
        entries.add(new CardEntry("Grass Energy", "Energy", List.of("Basic"), 55));
        var result = validator.validate(entries);
        assertThat(result.valid()).isTrue();
    }

    @Test
    void deck_without_basic_pokemon_fails() {
        var entries = new ArrayList<CardEntry>();
        entries.add(new CardEntry("Ivysaur", "Pokémon", List.of("Stage 1"), 4));
        entries.add(new CardEntry("Grass Energy", "Energy", List.of("Basic"), 56));
        var result = validator.validate(entries);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("Pokémon Básico"));
    }

    @Test
    void empty_deck_fails_with_multiple_errors() {
        var result = validator.validate(List.of());
        assertThat(result.valid()).isFalse();
        assertThat(result.errors().size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void error_messages_are_in_spanish() {
        var entries = List.of(new CardEntry("Grass Energy", "Energy", List.of("Basic"), 60));
        var result = validator.validate(entries);
        assertThat(result.errors()).anyMatch(e ->
            e.contains("Básico") || e.contains("básico") || e.contains("mazo"));
    }
}

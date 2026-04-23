package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.event.GameEvent;
import com.utn.pokemontcg.domain.engine.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD spec for AttackResolver — the 7-step Chain of Responsibility for attack resolution.
 *
 * Random injection: tail=0 means "heads" (coin flip succeeds), nextBoolean()=false means tails.
 * We use anonymous Random subclasses for deterministic coin flips.
 */
class AttackResolverTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    private static final String MATCH_ID = "match-1";

    private GameCard pokemonCard(String id, String name, int hp,
                                  List<Map<String, Object>> attacks,
                                  String weaknessType, String resistanceType) {
        return new GameCard(id, name, "Pokémon", List.of("Basic"),
                hp, attacks, weaknessType, resistanceType, 1);
    }

    private GameCard energyCard(String id, String type) {
        return new GameCard(id, type + " Energy", "Energy", List.of(type),
                0, List.of(), null, null, 0);
    }

    /**
     * Simple attack map compatible with GameCard.attacks format.
     * cost: list of energy type strings required.
     * damage: base damage string (e.g. "30", or "30+" for variable).
     */
    private Map<String, Object> attack(String name, List<String> cost, int damage) {
        return Map.of("name", name, "cost", cost, "damage", String.valueOf(damage));
    }

    private PokemonInPlay pip(GameCard card) {
        PokemonInPlay p = new PokemonInPlay(card);
        p.clearJustPlaced();
        return p;
    }

    private GameState buildState(PlayerState p1, PlayerState p2) {
        GameState state = new GameState(MATCH_ID, p1, p2);
        state.setMatchPhase(MatchPhase.ACTIVE);
        return state;
    }

    /** Random that always returns heads (true) for nextBoolean — coin flip passes. */
    private static final Random HEADS = new Random() {
        @Override public boolean nextBoolean() { return true; }
        @Override public int nextInt(int bound) { return 0; }
    };

    /** Random that always returns tails (false) for nextBoolean — coin flip fails. */
    private static final Random TAILS = new Random() {
        @Override public boolean nextBoolean() { return false; }
        @Override public int nextInt(int bound) { return 0; }
    };

    // ── fixtures ──────────────────────────────────────────────────────────────

    private PlayerState p1;
    private PlayerState p2;
    private GameState state;
    private AttackResolver resolver;

    @BeforeEach
    void setUp() {
        p1 = new PlayerState(1L);
        p2 = new PlayerState(2L);
        state = buildState(p1, p2);
        resolver = new AttackResolver(new DamageCalculator(), HEADS);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Step 1 — EnergyRequirementHandler
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void attack_cancelled_when_attacker_lacks_required_energy() {
        GameCard attackerCard = pokemonCard("xy1-001", "Bulbasaur", 60,
                List.of(attack("Scratch", List.of("Colorless"), 10)), null, null);
        PokemonInPlay attacker = pip(attackerCard);
        // no energies attached

        GameCard defenderCard = pokemonCard("xy1-002", "Charmander", 50,
                List.of(), null, null);
        PokemonInPlay defender = pip(defenderCard);

        p1.setActivePokemon(attacker);
        p2.setActivePokemon(defender);

        AttackContext ctx = new AttackContext(state, p1, p2, "Scratch", 10, "Grass");
        List<GameEvent> events = resolver.resolve(ctx);

        assertThat(ctx.isCancelled()).isTrue();
        assertThat(defender.getDamage()).isEqualTo(0);
        assertThat(events).isEmpty();
    }

    @Test
    void attack_proceeds_when_attacker_has_exact_energy_required() {
        GameCard attackerCard = pokemonCard("xy1-001", "Bulbasaur", 60,
                List.of(attack("Scratch", List.of("Colorless"), 10)), null, null);
        PokemonInPlay attacker = pip(attackerCard);
        attacker.getAttachedEnergyIds().add("Colorless");

        GameCard defenderCard = pokemonCard("xy1-002", "Charmander", 50,
                List.of(), null, null);
        PokemonInPlay defender = pip(defenderCard);

        p1.setActivePokemon(attacker);
        p2.setActivePokemon(defender);

        AttackContext ctx = new AttackContext(state, p1, p2, "Scratch", 10, "Grass");
        resolver.resolve(ctx);

        assertThat(ctx.isCancelled()).isFalse();
        assertThat(defender.getDamage()).isEqualTo(10);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Step 2 — ConfusionCheckHandler
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void confusion_tails_cancels_attack_and_deals_30_self_damage() {
        AttackResolver tailsResolver = new AttackResolver(new DamageCalculator(), TAILS);

        GameCard attackerCard = pokemonCard("xy1-001", "Bulbasaur", 60,
                List.of(attack("Scratch", List.of("Colorless"), 10)), null, null);
        PokemonInPlay attacker = pip(attackerCard);
        attacker.getAttachedEnergyIds().add("Colorless");
        attacker.setPrimaryStatus(StatusCondition.CONFUNDIDO);

        GameCard defenderCard = pokemonCard("xy1-002", "Charmander", 50,
                List.of(), null, null);
        PokemonInPlay defender = pip(defenderCard);

        p1.setActivePokemon(attacker);
        p2.setActivePokemon(defender);

        AttackContext ctx = new AttackContext(state, p1, p2, "Scratch", 10, "Grass");
        tailsResolver.resolve(ctx);

        assertThat(ctx.isCancelled()).isTrue();
        assertThat(attacker.getDamage()).isEqualTo(30);
        assertThat(defender.getDamage()).isEqualTo(0);
    }

    @Test
    void confusion_heads_attack_proceeds_normally() {
        GameCard attackerCard = pokemonCard("xy1-001", "Bulbasaur", 60,
                List.of(attack("Scratch", List.of("Colorless"), 10)), null, null);
        PokemonInPlay attacker = pip(attackerCard);
        attacker.getAttachedEnergyIds().add("Colorless");
        attacker.setPrimaryStatus(StatusCondition.CONFUNDIDO);

        GameCard defenderCard = pokemonCard("xy1-002", "Charmander", 50,
                List.of(), null, null);
        PokemonInPlay defender = pip(defenderCard);

        p1.setActivePokemon(attacker);
        p2.setActivePokemon(defender);

        AttackContext ctx = new AttackContext(state, p1, p2, "Scratch", 10, "Grass");
        resolver.resolve(ctx); // HEADS resolver

        assertThat(ctx.isCancelled()).isFalse();
        assertThat(attacker.getDamage()).isEqualTo(0);
        assertThat(defender.getDamage()).isEqualTo(10);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Step 6 — DamageCalculationHandler (weakness/resistance)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void weakness_doubles_damage() {
        // Grass attack vs Grass-weak defender
        GameCard attackerCard = pokemonCard("xy1-001", "Bulbasaur", 60,
                List.of(attack("Vine Whip", List.of("Grass"), 10)), null, null);
        PokemonInPlay attacker = pip(attackerCard);
        attacker.getAttachedEnergyIds().add("Grass");

        GameCard defenderCard = pokemonCard("xy1-002", "Squirtle", 60,
                List.of(), "Grass", null); // weak to Grass
        PokemonInPlay defender = pip(defenderCard);

        p1.setActivePokemon(attacker);
        p2.setActivePokemon(defender);

        AttackContext ctx = new AttackContext(state, p1, p2, "Vine Whip", 10, "Grass");
        resolver.resolve(ctx);

        // 10 * 2 = 20
        assertThat(defender.getDamage()).isEqualTo(20);
    }

    @Test
    void resistance_reduces_damage_by_20() {
        // Fire attack vs Fire-resistant defender
        GameCard attackerCard = pokemonCard("xy1-003", "Charmander", 50,
                List.of(attack("Ember", List.of("Fire"), 30)), null, null);
        PokemonInPlay attacker = pip(attackerCard);
        attacker.getAttachedEnergyIds().add("Fire");

        GameCard defenderCard = pokemonCard("xy1-004", "Ninetales", 80,
                List.of(), null, "Fire"); // resistant to Fire
        PokemonInPlay defender = pip(defenderCard);

        p1.setActivePokemon(attacker);
        p2.setActivePokemon(defender);

        AttackContext ctx = new AttackContext(state, p1, p2, "Ember", 30, "Fire");
        resolver.resolve(ctx);

        // 30 - 20 = 10
        assertThat(defender.getDamage()).isEqualTo(10);
    }

    @Test
    void zero_base_damage_attack_deals_no_damage_but_is_not_cancelled() {
        GameCard attackerCard = pokemonCard("xy1-001", "Bulbasaur", 60,
                List.of(attack("Sleep Powder", List.of("Grass"), 0)), null, null);
        PokemonInPlay attacker = pip(attackerCard);
        attacker.getAttachedEnergyIds().add("Grass");

        GameCard defenderCard = pokemonCard("xy1-002", "Charmander", 50,
                List.of(), null, null);
        PokemonInPlay defender = pip(defenderCard);

        p1.setActivePokemon(attacker);
        p2.setActivePokemon(defender);

        AttackContext ctx = new AttackContext(state, p1, p2, "Sleep Powder", 0, "Grass");
        resolver.resolve(ctx);

        assertThat(ctx.isCancelled()).isFalse();
        assertThat(defender.getDamage()).isEqualTo(0);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Step 7 — PostDamageEffectsHandler (KO detection via KnockoutProcessor)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void knockout_detected_after_lethal_damage_and_prize_drawn() {
        GameCard attackerCard = pokemonCard("xy1-001", "Bulbasaur", 60,
                List.of(attack("Mega Punch", List.of("Colorless", "Colorless"), 50)), null, null);
        PokemonInPlay attacker = pip(attackerCard);
        attacker.getAttachedEnergyIds().addAll(List.of("Colorless", "Colorless"));

        GameCard defenderCard = pokemonCard("xy1-002", "Charmander", 50,
                List.of(), null, null);
        PokemonInPlay defender = pip(defenderCard);

        // p1 has 6 prizes, p2 (attacker) also needs prizes for opponent draw
        GameCard prizeCard = new GameCard("prize-1", "Prize", "Pokémon", List.of("Basic"),
                40, List.of(), null, null, 1);
        p1.getPrizes().add(prizeCard); // p1's active gets KO'd, p2 draws p1's prize? No.
        // KO of p2's pokemon (defender belongs to p2) → p1 (attacker) draws from p1's own prizes
        // Actually: defender is p2's pokemon → p2 is owner of KO'd pokemon → p1 (opponent) draws prize
        // p1's prizes list represents cards p1 can draw when they KO opponent's pokemon
        p1.getPrizes().clear();
        GameCard p1prize = new GameCard("p1-prize-1", "Bulbasaur", "Pokémon", List.of("Basic"),
                60, List.of(), null, null, 1);
        p1.getPrizes().add(p1prize);

        p1.setActivePokemon(attacker);
        p2.setActivePokemon(defender);

        AttackContext ctx = new AttackContext(state, p1, p2, "Mega Punch", 50, "Colorless");
        List<GameEvent> events = resolver.resolve(ctx);

        // defender KO'd (50 damage >= 50 hp)
        assertThat(defender.getDamage()).isEqualTo(50);
        // p2 active is now null
        assertThat(p2.getActivePokemon()).isNull();
        // p1 drew a prize
        assertThat(p1.getPrizes()).isEmpty();
        assertThat(p1.getHand()).hasSize(1);
        // events include KO and prize taken
        assertThat(events).anyMatch(e -> e instanceof GameEvent.PokemonKnockedOut);
        assertThat(events).anyMatch(e -> e instanceof GameEvent.PrizeTaken);
    }

    // ════════════════════════════════════════════════════════════════════════
    // AttackDeclared event emitted
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void attack_declared_event_emitted_for_valid_attack() {
        GameCard attackerCard = pokemonCard("xy1-001", "Bulbasaur", 60,
                List.of(attack("Scratch", List.of("Colorless"), 10)), null, null);
        PokemonInPlay attacker = pip(attackerCard);
        attacker.getAttachedEnergyIds().add("Colorless");

        GameCard defenderCard = pokemonCard("xy1-002", "Charmander", 50,
                List.of(), null, null);
        PokemonInPlay defender = pip(defenderCard);

        p1.setActivePokemon(attacker);
        p2.setActivePokemon(defender);

        AttackContext ctx = new AttackContext(state, p1, p2, "Scratch", 10, "Grass");
        List<GameEvent> events = resolver.resolve(ctx);

        assertThat(events).anyMatch(e ->
                e instanceof GameEvent.AttackDeclared ad
                && ad.attackerId().equals(1L)
                && ad.attackName().equals("Scratch"));
        assertThat(events).anyMatch(e ->
                e instanceof GameEvent.DamageDealt dd && dd.amount() == 10);
    }

    // ════════════════════════════════════════════════════════════════════════
    // turnFlags.attackDoneThisTurn set after resolve
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void attack_done_flag_set_after_successful_resolve() {
        GameCard attackerCard = pokemonCard("xy1-001", "Bulbasaur", 60,
                List.of(attack("Scratch", List.of("Colorless"), 10)), null, null);
        PokemonInPlay attacker = pip(attackerCard);
        attacker.getAttachedEnergyIds().add("Colorless");

        GameCard defenderCard = pokemonCard("xy1-002", "Charmander", 50,
                List.of(), null, null);
        PokemonInPlay defender = pip(defenderCard);

        p1.setActivePokemon(attacker);
        p2.setActivePokemon(defender);

        AttackContext ctx = new AttackContext(state, p1, p2, "Scratch", 10, "Grass");
        resolver.resolve(ctx);

        assertThat(state.getTurnFlags().isAttackDoneThisTurn()).isTrue();
    }

    @Test
    void attack_done_flag_not_set_when_attack_cancelled_by_confusion() {
        AttackResolver tailsResolver = new AttackResolver(new DamageCalculator(), TAILS);

        GameCard attackerCard = pokemonCard("xy1-001", "Bulbasaur", 60,
                List.of(attack("Scratch", List.of("Colorless"), 10)), null, null);
        PokemonInPlay attacker = pip(attackerCard);
        attacker.getAttachedEnergyIds().add("Colorless");
        attacker.setPrimaryStatus(StatusCondition.CONFUNDIDO);

        GameCard defenderCard = pokemonCard("xy1-002", "Charmander", 50,
                List.of(), null, null);
        PokemonInPlay defender = pip(defenderCard);

        p1.setActivePokemon(attacker);
        p2.setActivePokemon(defender);

        AttackContext ctx = new AttackContext(state, p1, p2, "Scratch", 10, "Grass");
        tailsResolver.resolve(ctx);

        assertThat(state.getTurnFlags().isAttackDoneThisTurn()).isFalse();
    }
}

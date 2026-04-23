package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.event.GameEvent;
import com.utn.pokemontcg.domain.engine.model.PokemonInPlay;
import com.utn.pokemontcg.domain.engine.model.StatusCondition;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Entry point for the 7-step attack resolution pipeline (ENG-06).
 *
 * Chain order:
 *   1. EnergyRequirementHandler
 *   2. ConfusionCheckHandler
 *   3. AttackDeclaredHandler  (emits AttackDeclared event)
 *   4. DamageCalculationHandler
 *   5. PostDamageEffectsHandler (KO detection, prize draw)
 *   6. AttackFlagHandler (sets turnFlags.attackDoneThisTurn)
 */
public class AttackResolver {

    private final DamageCalculator damageCalculator;
    private final Random random;
    private final KnockoutProcessor knockoutProcessor;

    public AttackResolver(DamageCalculator damageCalculator, Random random) {
        this.damageCalculator = damageCalculator;
        this.random = random;
        this.knockoutProcessor = new KnockoutProcessor();
    }

    /**
     * Runs the full attack resolution chain for the given context.
     *
     * @return all events emitted during resolution (never null)
     */
    public List<GameEvent> resolve(AttackContext ctx) {
        AttackHandler chain = buildChain();
        chain.handle(ctx, TERMINAL);
        return ctx.getEvents();
    }

    // ── Chain construction ────────────────────────────────────────────────

    private AttackHandler buildChain() {
        return link(
            energyRequirementHandler(),
            link(confusionCheckHandler(),
            link(attackDeclaredHandler(),
            link(damageCalculationHandler(),
            link(postDamageEffectsHandler(),
                 attackFlagHandler())))));
    }

    /** Wraps {@code current} so it calls {@code next} after itself (if not cancelled). */
    private static AttackHandler link(AttackHandler current, AttackHandler next) {
        return (ctx, ignored) -> current.handle(ctx, next);
    }

    private static final AttackHandler TERMINAL = (ctx, next) -> { /* end of chain */ };

    // ── Handler implementations ───────────────────────────────────────────

    /**
     * Step 1 — Verify attacker has enough energy for the declared attack.
     * Cancels silently if energy is insufficient.
     */
    private AttackHandler energyRequirementHandler() {
        return (ctx, next) -> {
            PokemonInPlay attacker = ctx.getAttacker().getActivePokemon();
            List<String> required = resolveAttackCost(ctx);

            if (!hasEnoughEnergy(attacker.getAttachedEnergyIds(), required)) {
                ctx.cancel();
                return;
            }
            next.handle(ctx, TERMINAL);
        };
    }

    /**
     * Step 2 — If attacker is CONFUNDIDO, flip a coin.
     * Tails: cancel attack, deal 30 damage to attacker.
     * Heads: continue.
     */
    private AttackHandler confusionCheckHandler() {
        return (ctx, next) -> {
            PokemonInPlay attacker = ctx.getAttacker().getActivePokemon();
            if (attacker.getPrimaryStatus() == StatusCondition.CONFUNDIDO) {
                boolean heads = random.nextBoolean();
                if (!heads) {
                    attacker.addDamage(30);
                    ctx.cancel();
                    return;
                }
            }
            next.handle(ctx, TERMINAL);
        };
    }

    /**
     * Step 3 — Emit AttackDeclared event.
     */
    private AttackHandler attackDeclaredHandler() {
        return (ctx, next) -> {
            ctx.addEvent(new GameEvent.AttackDeclared(
                    ctx.getState().getMatchId(),
                    ctx.getAttacker().getUserId(),
                    ctx.getAttackName()));
            next.handle(ctx, TERMINAL);
        };
    }

    /**
     * Step 4 — Calculate final damage using DamageCalculator (weakness/resistance/rounding).
     * Emits DamageDealt event if damage > 0.
     */
    private AttackHandler damageCalculationHandler() {
        return (ctx, next) -> {
            PokemonInPlay attacker = ctx.getAttacker().getActivePokemon();
            PokemonInPlay defender = ctx.getDefender().getActivePokemon();

            int finalDamage = damageCalculator.calculate(
                    ctx.getBaseAttackDamage(),
                    attacker,
                    defender,
                    List.of(ctx.getAttackerType()));

            defender.addDamage(finalDamage);

            if (finalDamage > 0) {
                ctx.addEvent(new GameEvent.DamageDealt(
                        ctx.getState().getMatchId(),
                        attacker.getCard().id(),
                        defender.getCard().id(),
                        finalDamage));
            }

            next.handle(ctx, TERMINAL);
        };
    }

    /**
     * Step 5 — Post-damage effects: KO detection, prize draw, KO events.
     */
    private AttackHandler postDamageEffectsHandler() {
        return (ctx, next) -> {
            PokemonInPlay defender = ctx.getDefender().getActivePokemon();
            if (defender != null && defender.isKnockedOut()) {
                List<GameEvent> koEvents = knockoutProcessor.processKnockout(
                        ctx.getState(),
                        ctx.getDefender(),
                        defender);
                koEvents.forEach(ctx::addEvent);
            }
            next.handle(ctx, TERMINAL);
        };
    }

    /**
     * Step 6 — Mark attack as done in turn flags.
     */
    private AttackHandler attackFlagHandler() {
        return (ctx, next) -> {
            ctx.getState().getTurnFlags().setAttackDoneThisTurn(true);
            next.handle(ctx, TERMINAL);
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Extracts the cost list for the declared attack from the attacker's card definition.
     * Returns empty list if attack not found (no energy required).
     */
    @SuppressWarnings("unchecked")
    private List<String> resolveAttackCost(AttackContext ctx) {
        PokemonInPlay attacker = ctx.getAttacker().getActivePokemon();
        String attackName = ctx.getAttackName();

        for (Map<String, Object> atk : attacker.getCard().attacks()) {
            if (attackName.equals(atk.get("name"))) {
                Object cost = atk.get("cost");
                if (cost instanceof List<?>) {
                    return (List<String>) cost;
                }
            }
        }
        return List.of();
    }

    /**
     * Returns true if the attached energies satisfy all required energy types.
     * Each required type must be matched by one attached energy (multiset subset check).
     * "Colorless" can be satisfied by any energy type.
     */
    private boolean hasEnoughEnergy(List<String> attached, List<String> required) {
        if (required.isEmpty()) return true;

        // count available energies
        java.util.Map<String, Integer> pool = new java.util.HashMap<>();
        for (String e : attached) {
            pool.merge(e, 1, Integer::sum);
        }

        int colorlessNeeded = 0;
        for (String req : required) {
            if ("Colorless".equals(req)) {
                colorlessNeeded++;
            } else {
                int have = pool.getOrDefault(req, 0);
                if (have <= 0) return false;
                pool.put(req, have - 1);
            }
        }

        // colorless can be any remaining energy
        int totalRemaining = pool.values().stream().mapToInt(Integer::intValue).sum();
        return totalRemaining >= colorlessNeeded;
    }
}

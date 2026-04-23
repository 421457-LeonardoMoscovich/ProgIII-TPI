package com.utn.pokemontcg.domain.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry that maps a composite key {@code (cardId + "#" + effectName)} to an
 * {@link AttackEffect} lambda.
 *
 * <p>Thread-safety is intentionally out of scope — game state is always owned by
 * a single thread during engine processing.
 */
public class EffectRegistry {

    private static final String KEY_SEPARATOR = "#";

    private final Map<String, AttackEffect> effects = new HashMap<>();

    /**
     * Registers an effect under the composite key {@code cardId + "#" + effectName}.
     * Overwrites any previously registered effect for the same key.
     *
     * @param cardId     the card identifier (e.g. {@code "xy1-001"})
     * @param effectName the attack or effect name (e.g. {@code "Scratch"})
     * @param effect     the {@link AttackEffect} lambda to associate
     */
    public void register(String cardId, String effectName, AttackEffect effect) {
        effects.put(compositeKey(cardId, effectName), effect);
    }

    /**
     * Looks up the effect for the given composite key.
     *
     * @param cardId     the card identifier
     * @param effectName the attack or effect name
     * @return an {@link Optional} containing the effect, or empty if not registered
     */
    public Optional<AttackEffect> find(String cardId, String effectName) {
        return Optional.ofNullable(effects.get(compositeKey(cardId, effectName)));
    }

    /**
     * Returns {@code true} if an effect is registered for the given composite key.
     *
     * @param cardId     the card identifier
     * @param effectName the attack or effect name
     * @return {@code true} if present, {@code false} otherwise
     */
    public boolean hasEffect(String cardId, String effectName) {
        return effects.containsKey(compositeKey(cardId, effectName));
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    private static String compositeKey(String cardId, String effectName) {
        return cardId + KEY_SEPARATOR + effectName;
    }
}

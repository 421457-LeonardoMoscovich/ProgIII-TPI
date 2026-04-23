package com.utn.pokemontcg.domain.engine;

/**
 * Single link in the attack resolution Chain of Responsibility.
 * Each handler processes its step then calls {@code next.handle(ctx)} to continue,
 * or skips the call to short-circuit the chain.
 */
@FunctionalInterface
public interface AttackHandler {
    void handle(AttackContext ctx, AttackHandler next);
}

package com.utn.pokemontcg.domain.engine;

/**
 * Result of applying a {@link com.utn.pokemontcg.domain.engine.action.GameAction}.
 * Either ok (action applied) or rejected (action blocked with a reason).
 */
public final class ActionResult {

    private final boolean ok;
    private final String rejectionReason;

    private ActionResult(boolean ok, String rejectionReason) {
        this.ok = ok;
        this.rejectionReason = rejectionReason;
    }

    public static ActionResult ok() {
        return new ActionResult(true, null);
    }

    public static ActionResult rejected(String reason) {
        return new ActionResult(false, reason);
    }

    public boolean isOk() { return ok; }
    public String getRejectionReason() { return rejectionReason; }

    @Override
    public String toString() {
        return ok ? "ActionResult[OK]" : "ActionResult[REJECTED: " + rejectionReason + "]";
    }
}

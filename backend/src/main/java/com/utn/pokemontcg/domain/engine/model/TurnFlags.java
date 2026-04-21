package com.utn.pokemontcg.domain.engine.model;

public class TurnFlags {
    private boolean supporterPlayedThisTurn;
    private boolean attackDoneThisTurn;
    private boolean energyAttachedThisTurn;
    private boolean retreatedThisTurn;

    public boolean isSupporterPlayedThisTurn() { return supporterPlayedThisTurn; }
    public void setSupporterPlayedThisTurn(boolean v) { this.supporterPlayedThisTurn = v; }
    public boolean isAttackDoneThisTurn() { return attackDoneThisTurn; }
    public void setAttackDoneThisTurn(boolean v) { this.attackDoneThisTurn = v; }
    public boolean isEnergyAttachedThisTurn() { return energyAttachedThisTurn; }
    public void setEnergyAttachedThisTurn(boolean v) { this.energyAttachedThisTurn = v; }
    public boolean isRetreatedThisTurn() { return retreatedThisTurn; }
    public void setRetreatedThisTurn(boolean v) { this.retreatedThisTurn = v; }

    public void reset() {
        supporterPlayedThisTurn = false;
        attackDoneThisTurn = false;
        energyAttachedThisTurn = false;
        retreatedThisTurn = false;
    }
}

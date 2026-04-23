package com.utn.pokemontcg.domain.engine;

import com.utn.pokemontcg.domain.engine.action.GameAction;
import com.utn.pokemontcg.domain.engine.event.GameEvent;
import com.utn.pokemontcg.domain.engine.model.*;

import java.util.*;

/**
 * Facade (ENG-10) — single public entry point for all game engine operations.
 *
 * <p>Hides the internal components (MatchSetup, TurnManager, AttackResolver,
 * StatusEffectManager, VictoryConditionChecker, KnockoutProcessor) behind a
 * clean API. Has no Spring/JPA/WebSocket dependencies.
 */
public class GameEngineFacade {

    private final MatchSetup matchSetup;
    private final TurnManager turnManager;
    private final AttackResolver attackResolver;
    private final StatusEffectManager statusEffectManager;
    private final VictoryConditionChecker victoryChecker;

    private List<GameEvent> lastEvents = new ArrayList<>();

    public GameEngineFacade() {
        this.matchSetup           = new MatchSetup();
        this.turnManager          = new TurnManager();
        this.attackResolver       = new AttackResolver(new DamageCalculator(), new Random());
        this.statusEffectManager  = new StatusEffectManager();
        this.victoryChecker       = new VictoryConditionChecker();
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Initialises a new match: shuffles decks, deals hands, places prizes, sets ACTIVE phase.
     *
     * @param p1Id   player-1 userId
     * @param deck1  player-1's 60-card deck (mutated — cards moved to hand/prizes/deck)
     * @param p2Id   player-2 userId
     * @param deck2  player-2's 60-card deck
     * @param seed   RNG seed for deterministic shuffling (use {@code new Random().nextLong()} in prod)
     * @return the initialised {@link GameState}
     */
    public GameState startMatch(Long p1Id, List<GameCard> deck1,
                                Long p2Id, List<GameCard> deck2,
                                long seed) {
        PlayerState p1 = new PlayerState(p1Id);
        PlayerState p2 = new PlayerState(p2Id);
        p1.getDeck().addAll(deck1);
        p2.getDeck().addAll(deck2);

        String matchId = UUID.randomUUID().toString();
        GameState state = new GameState(matchId, p1, p2);

        lastEvents = matchSetup.setupMatch(state, new Random(seed));

        // Set turn phase: player1 goes first and skips DRAW on turn 0
        state.setTurnPhase(turnManager.getInitialPhaseFor(state));

        return state;
    }

    /**
     * Applies a player action to the game state.
     *
     * @return {@link ActionResult#ok()} if the action was applied,
     *         {@link ActionResult#rejected(String)} with a reason if blocked
     */
    public ActionResult applyAction(GameState state, GameAction action) {
        // Guard: only the current player may act
        if (!action.userId().equals(state.getCurrentPlayer().getUserId())) {
            return ActionResult.rejected("not your turn");
        }

        lastEvents = new ArrayList<>();

        return switch (action) {
            case GameAction.PlayBasicPokemon a -> handlePlayBasic(state, a);
            case GameAction.AttachEnergy a     -> handleAttachEnergy(state, a);
            case GameAction.Attack a           -> handleAttack(state, a);
            case GameAction.Pass a             -> handlePass(state);
            case GameAction.PlayTrainer a      -> ActionResult.ok(); // ENG-12 scope
            case GameAction.Evolve a           -> ActionResult.ok(); // future sprint
            case GameAction.Retreat a          -> ActionResult.ok(); // future sprint
        };
    }

    /**
     * Processes between-turns effects for both players' active Pokémon,
     * then advances the turn (swaps players).
     *
     * @return events emitted during between-turns processing
     */
    public List<GameEvent> processBetweenTurns(GameState state) {
        List<GameEvent> events = new ArrayList<>();

        // Status effects on current player's active
        applyStatusEffects(state.getCurrentPlayer(), events, state);
        // Status effects on waiting player's active
        applyStatusEffects(state.getWaitingPlayer(), events, state);

        // Emit TurnEnded before swap
        events.add(new GameEvent.TurnEnded(
                state.getMatchId(),
                state.getCurrentPlayer().getUserId(),
                state.getGlobalTurn()));

        // Advance: swap players, increment globalTurn, reset TurnFlags
        turnManager.endTurn(state);

        lastEvents = events;
        return events;
    }

    /**
     * Evaluates all win conditions against the current state.
     *
     * @return present if a winner (or sudden death) is determined; empty if game continues
     */
    public Optional<VictoryConditionChecker.VictoryResult> checkVictory(GameState state) {
        return victoryChecker.check(state);
    }

    /**
     * Returns the events emitted by the most recent engine call.
     * Useful for tests and for the WebSocket notification layer.
     */
    public List<GameEvent> getLastEvents() {
        return Collections.unmodifiableList(lastEvents);
    }

    // ── Action handlers ───────────────────────────────────────────────────

    private ActionResult handlePlayBasic(GameState state, GameAction.PlayBasicPokemon action) {
        PlayerState player = state.getCurrentPlayer();

        // Find card in hand
        Optional<GameCard> cardOpt = player.getHand().stream()
                .filter(c -> c.id().equals(action.cardId()))
                .findFirst();

        if (cardOpt.isEmpty()) {
            return ActionResult.rejected("card not in hand: " + action.cardId());
        }

        GameCard card = cardOpt.get();

        if (!"Pokémon".equals(card.supertype()) || !card.subtypes().contains("Basic")) {
            return ActionResult.rejected("card is not a Basic Pokémon: " + action.cardId());
        }

        player.getHand().remove(card);
        PokemonInPlay pip = new PokemonInPlay(card);

        if (!action.toBench()) {
            if (player.getActivePokemon() != null) {
                return ActionResult.rejected("active slot already occupied");
            }
            player.setActivePokemon(pip);
        } else {
            if (player.getBench().size() >= 5) {
                return ActionResult.rejected("bench is full");
            }
            player.getBench().add(pip);
        }

        lastEvents.add(new GameEvent.PokemonPlayed(
                state.getMatchId(), player.getUserId(), card.id(), action.toBench()));

        return ActionResult.ok();
    }

    private ActionResult handleAttachEnergy(GameState state, GameAction.AttachEnergy action) {
        PlayerState player = state.getCurrentPlayer();

        if (state.getTurnFlags().isEnergyAttachedThisTurn()) {
            return ActionResult.rejected("already attached energy this turn");
        }

        Optional<GameCard> energyOpt = player.getHand().stream()
                .filter(c -> c.id().equals(action.energyCardId()))
                .findFirst();

        if (energyOpt.isEmpty()) {
            return ActionResult.rejected("energy card not in hand");
        }

        PokemonInPlay target = findPokemonInPlay(player, action.targetInPlayId());
        if (target == null) {
            return ActionResult.rejected("target pokemon not found: " + action.targetInPlayId());
        }

        GameCard energy = energyOpt.get();
        player.getHand().remove(energy);
        // Energy type = first subtype (e.g. "Fire", "Colorless")
        String energyType = energy.subtypes().isEmpty() ? "Colorless" : energy.subtypes().get(0);
        target.getAttachedEnergyIds().add(energyType);
        state.getTurnFlags().setEnergyAttachedThisTurn(true);

        lastEvents.add(new GameEvent.EnergyAttached(
                state.getMatchId(), player.getUserId(), energy.id(), action.targetInPlayId()));

        return ActionResult.ok();
    }

    private ActionResult handleAttack(GameState state, GameAction.Attack action) {
        PlayerState attacker = state.getCurrentPlayer();
        PlayerState defender = state.getWaitingPlayer();

        PokemonInPlay attackingPoke = attacker.getActivePokemon();
        if (attackingPoke == null) {
            return ActionResult.rejected("no active pokemon to attack with");
        }
        if (defender.getActivePokemon() == null) {
            return ActionResult.rejected("opponent has no active pokemon");
        }

        List<Map<String, Object>> attacks = attackingPoke.getCard().attacks();
        if (action.attackIndex() < 0 || action.attackIndex() >= attacks.size()) {
            return ActionResult.rejected("invalid attack index: " + action.attackIndex());
        }

        Map<String, Object> atk = attacks.get(action.attackIndex());
        String attackName = (String) atk.get("name");
        int baseDamage = parseDamage(atk.get("damage"));
        String attackerType = attackingPoke.getCard().weaknessType() != null
                ? attackingPoke.getCard().weaknessType()
                : "Colorless";

        AttackContext ctx = new AttackContext(state, attacker, defender,
                attackName, baseDamage, attackerType);

        List<GameEvent> attackEvents = attackResolver.resolve(ctx);
        lastEvents.addAll(attackEvents);

        if (ctx.isCancelled()) {
            return ActionResult.rejected("attack cancelled (confusion or energy)");
        }

        return ActionResult.ok();
    }

    private ActionResult handlePass(GameState state) {
        state.setTurnPhase(TurnPhase.BETWEEN_TURNS);
        return ActionResult.ok();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void applyStatusEffects(PlayerState player, List<GameEvent> events, GameState state) {
        PokemonInPlay active = player.getActivePokemon();
        if (active == null) return;
        statusEffectManager.processBetweenTurns(active, new Random());
    }

    private PokemonInPlay findPokemonInPlay(PlayerState player, String cardId) {
        PokemonInPlay active = player.getActivePokemon();
        if (active != null && active.getCard().id().equals(cardId)) return active;
        return player.getBench().stream()
                .filter(p -> p.getCard().id().equals(cardId))
                .findFirst()
                .orElse(null);
    }

    private int parseDamage(Object damageObj) {
        if (damageObj == null) return 0;
        String s = damageObj.toString().replace("+", "").replace("×", "").trim();
        if (s.isEmpty()) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }
}

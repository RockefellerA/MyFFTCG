package shufflingway;

import java.util.EnumSet;
import java.util.List;

/**
 * Bridge interface that gives {@link ActionResolver} controlled access to the
 * live game state without coupling it directly to {@code MainWindow}'s private
 * fields.
 *
 * <p>MainWindow creates an anonymous implementation of this interface when
 * invoking {@link ActionResolver#resolve} and supplies the lambdas that dip
 * into the correct parallel lists.
 */
public interface GameContext {

    /** Appends a timestamped line to the game log. */
    void logEntry(String message);

    // ---- P1 forwards --------------------------------------------------------

    /** Number of P1 forwards currently on the field. */
    int p1ForwardCount();

    /**
     * The effective {@link CardData} for P1's forward at {@code idx}.
     * Returns the top (primed) card when the slot is in a Primed state.
     */
    CardData p1Forward(int idx);

    /** Accumulated damage on P1's forward at {@code idx}. */
    int p1ForwardCurrentDamage(int idx);

    /** Field state (ACTIVE / DULL / BRAVE_ATTACKED) of P1's forward at {@code idx}. */
    CardState p1ForwardState(int idx);

    /**
     * Applies {@code amount} damage to P1's forward at {@code idx}, refreshes
     * the slot, and breaks the forward if its remaining power reaches zero.
     */
    void damageP1Forward(int idx, int amount);

    // ---- P2 forwards --------------------------------------------------------

    /** Number of P2 forwards currently on the field. */
    int p2ForwardCount();

    /** The {@link CardData} for P2's forward at {@code idx}. */
    CardData p2Forward(int idx);

    /** Accumulated damage on P2's forward at {@code idx}. */
    int p2ForwardCurrentDamage(int idx);

    /** Field state of P2's forward at {@code idx}. */
    CardState p2ForwardState(int idx);

    /**
     * Applies {@code amount} damage to P2's forward at {@code idx}, refreshes
     * the slot, and breaks the forward if its remaining power reaches zero.
     */
    void damageP2Forward(int idx, int amount);

    // ---- Targeted selection -------------------------------------------------

    /**
     * Shows a modal dialog letting P1 choose up to {@code maxCount} eligible
     * field cards and returns their targets.
     *
     * @param maxCount     maximum number of cards the player may select
     * @param upTo         if {@code true} the player may confirm with fewer than {@code maxCount}
     * @param opponentOnly if {@code true} only P2's cards are offered as targets
     * @param selfOnly     if {@code true} only P1's cards are eligible
     * @param condition    optional eligibility filter: {@code "active"}, {@code "dull"},
     *                     {@code "damaged"}, {@code "attacking"}, {@code "blocking"},
     *                     or {@code null} for any
     * @param element      optional element name to restrict targets; {@code null} = any
     * @param costVal      CP cost filter value; {@code -1} = no filter
     * @param costCmp      {@code "less"}, {@code "more"}, or {@code null} for exact
     * @param forwards     include Forwards as eligible targets
     * @param backups      include Backups as eligible targets
     * @param monsters     include Monsters as eligible targets
     */
    List<ForwardTarget> selectCharacters(int maxCount, boolean upTo, boolean opponentOnly,
            boolean selfOnly, String condition, String element, int costVal, String costCmp,
            boolean forwards, boolean backups, boolean monsters);

    /**
     * Shows a modal dialog letting P1 choose up to {@code maxCount} eligible
     * cards from a Break Zone and returns their targets.
     *
     * @param opponentZone if {@code true}, selects from P2's Break Zone; otherwise P1's
     * @param condition    optional eligibility filter; {@code null} = any
     * @param element      optional element name to restrict targets; {@code null} = any
     * @param costVal      CP cost filter value; {@code -1} = no filter
     * @param costCmp      {@code "less"}, {@code "more"}, or {@code null} for exact
     * @param forwards     include Forwards as eligible targets
     * @param backups      include Backups as eligible targets
     * @param monsters     include Monsters as eligible targets
     */
    List<ForwardTarget> selectCharactersFromBreakZone(int maxCount, boolean upTo,
            boolean opponentZone, String condition, String element, int costVal, String costCmp,
            boolean forwards, boolean backups, boolean monsters);

    // ---- Zone-dispatch single-target effects --------------------------------

    /**
     * Applies {@code amount} damage to the target.
     * Only meaningful for Forwards and Monsters (Backups are ignored).
     */
    void damageTarget(ForwardTarget t, int amount);

    /** Sets the target back to Active state and refreshes its slot. */
    void activateTarget(ForwardTarget t);

    /** Dulls the target and refreshes its slot. */
    void dullTarget(ForwardTarget t);

    /** Freezes the target (skips activation next Active Phase) and refreshes its slot. */
    void freezeTarget(ForwardTarget t);

    /** Dulls and freezes the target. */
    void dullAndFreezeTarget(ForwardTarget t);

    /** Breaks the target (sends to the owning player's Break Zone). */
    void breakTarget(ForwardTarget t);

    /** Removes the target from the game permanently (not to the Break Zone). */
    void removeTargetFromGame(ForwardTarget t);

    /**
     * Plays the target (chosen from a Break Zone) onto the field without
     * paying costs.  Forwards go to the forward zone, Backups to a backup
     * slot, Monsters to the monster zone.
     */
    void playTargetOntoField(ForwardTarget t);

    /** Moves the target (chosen from a Break Zone) to P1's hand. */
    void addTargetToHand(ForwardTarget t);

    /**
     * Adds {@code amount} power and optionally grants {@code traits} to the target
     * until the end of the turn.
     */
    void boostTarget(ForwardTarget t, int amount, EnumSet<CardData.Trait> traits);

    /**
     * Finds {@code source} on P1's forward zone and adds {@code amount} power and
     * optionally grants {@code traits} to it until the end of the turn.
     * No-op if the source card is not found on the field.
     */
    void boostSourceForward(CardData source, int amount, EnumSet<CardData.Trait> traits);

    /**
     * Reduces the target's power by {@code amount} and temporarily removes {@code traits}
     * until the end of the turn.  If effective power drops to 0 or below the card is sent
     * to the break zone (not treated as "broken" mechanically — distinction TBD).
     */
    void reduceTarget(ForwardTarget t, int amount, EnumSet<CardData.Trait> traits);

    /**
     * Finds {@code source} on P1's forward zone and applies the same reduction as
     * {@link #reduceTarget}.  No-op if the source card is not found on the field.
     */
    void reduceSourceForward(CardData source, int amount, EnumSet<CardData.Trait> traits);

    // ---- Computed-damage queries -----------------------------------------------

    /** Returns the highest effective power among all P1 Forwards on the field; {@code 0} if none. */
    int highestP1ForwardPower();

    /**
     * Returns the effective power of the first field Forward or Monster whose name matches
     * {@code cardName} (case-insensitive), searching P1's zones then P2's.
     * Returns {@code -1} if no matching card is found.
     */
    int fieldForwardPowerByName(String cardName);

    /**
     * Returns the effective power of the target Forward or Monster.
     * Returns {@code 0} for Backups or out-of-range indices.
     */
    int effectiveTargetPower(ForwardTarget t);

    /**
     * Forces the ability-user's opponent to discard {@code count} cards from hand
     * to their Break Zone.  No CP is generated.
     * When P1 is the ability user, P2 AI discards automatically (worst cards first).
     * When P2 is the ability user, P1 is prompted via a selection dialog.
     */
    void forceOpponentDiscard(int count);

    /**
     * Draws {@code count} cards from the top of the ability user's deck into their hand.
     */
    void drawCards(int count);

    /**
     * Prompts the ability user to discard {@code count} cards from their hand to
     * their Break Zone.  No CP is generated.
     * When P1 is the ability user, a selection dialog is shown.
     * When P2 is the ability user, the AI discards automatically (worst cards first).
     */
    void selfDiscard(int count);

    /**
     * Flips {@code amount} cards from the opponent's deck into their damage zone,
     * using the same mechanic as attack-phase damage (EX Burst triggers included).
     * When P1 is the ability user the opponent is P2, and vice versa.
     */
    void dealDamageToOpponent(int amount);

    /**
     * Flips {@code amount} cards from the ability user's own deck into their damage zone,
     * using the same mechanic as attack-phase damage (EX Burst triggers included).
     * When P1 is the ability user the self is P1, and vice versa.
     */
    void dealDamageToSelf(int amount);

    // ---- Dull effects (used by mass-effect; also available individually) ----

    /** Dulls P1's forward at {@code idx} and refreshes its slot. */
    void dullP1Forward(int idx);

    /** Dulls P2's forward at {@code idx} and refreshes its slot. */
    void dullP2Forward(int idx);

    /** Freezes P1's forward at {@code idx} (blue tint; skips activation next Active Phase). */
    void freezeP1Forward(int idx);

    /** Freezes P2's forward at {@code idx} (blue tint; skips activation next Active Phase). */
    void freezeP2Forward(int idx);

    // ---- Block restrictions -------------------------------------------------

    /** Prevents P1's forward at {@code idx} from being chosen as a blocker this turn. */
    void setP1ForwardCannotBlock(int idx);

    /** Prevents P2's forward at {@code idx} from being chosen as a blocker this turn. */
    void setP2ForwardCannotBlock(int idx);

    /** Requires P1's forward at {@code idx} to block this turn if it is eligible to do so. */
    void setP1ForwardMustBlock(int idx);

    /** Requires P2's forward at {@code idx} to block this turn if it is eligible to do so. */
    void setP2ForwardMustBlock(int idx);

    // ---- Return to deck -----------------------------------------------------

    /**
     * Prompts the active player to choose whether {@code cardName} should be placed on top
     * or at the bottom of the deck.
     *
     * @return {@code true} if the player chose "Top", {@code false} for "Bottom"
     */
    boolean askTopOrBottom(String cardName);

    /** Removes P1's forward at {@code idx} from the field and adds it to P1's hand. */
    void returnP1ForwardToHand(int idx);

    /** Removes P2's forward at {@code idx} from the field and adds it to P2's hand. */
    void returnP2ForwardToHand(int idx);

    /** Removes P1's forward at {@code idx} from the field and places it at the bottom of P1's deck. */
    void returnP1ForwardToDeckBottom(int idx);

    /** Removes P2's forward at {@code idx} from the field and places it at the bottom of P2's deck. */
    void returnP2ForwardToDeckBottom(int idx);

    /** Removes P1's forward at {@code idx} from the field and places it on top of P1's deck. */
    void returnP1ForwardToDeckTop(int idx);

    /** Removes P2's forward at {@code idx} from the field and places it on top of P2's deck. */
    void returnP2ForwardToDeckTop(int idx);

    // ---- Attack restrictions ------------------------------------------------

    /** Prevents P1's forward at {@code idx} from attacking this turn. */
    void setP1ForwardCannotAttack(int idx);

    /** Prevents P2's forward at {@code idx} from attacking this turn. */
    void setP2ForwardCannotAttack(int idx);

    /** Requires P1's forward at {@code idx} to attack this turn if it is eligible to do so. */
    void setP1ForwardMustAttack(int idx);

    /** Requires P2's forward at {@code idx} to attack this turn if it is eligible to do so. */
    void setP2ForwardMustAttack(int idx);

    /**
     * Prevents P1's forward at {@code idx} from attacking or blocking until the end of P1's turn
     * (survives P2's end-phase clearing, cleared at P1's end phase).
     */
    void setP1ForwardCannotAttackOrBlockPersistent(int idx);

    /**
     * Prevents P2's forward at {@code idx} from attacking or blocking until the end of P2's turn
     * (survives P1's end-phase clearing, cleared at P2's end phase).
     */
    void setP2ForwardCannotAttackOrBlockPersistent(int idx);

    // ---- Attack / block state queries ---------------------------------------

    /** Returns {@code true} if P1's forward at {@code idx} is currently declared as an attacker. */
    boolean isP1ForwardAttacking(int idx);

    /** Returns {@code true} if P2's forward at {@code idx} is currently declared as an attacker. */
    boolean isP2ForwardAttacking(int idx);

    /** Returns {@code true} if P1's forward at {@code idx} is currently declared as a blocker. */
    boolean isP1ForwardBlocking(int idx);

    /** Returns {@code true} if P2's forward at {@code idx} is currently declared as a blocker. */
    boolean isP2ForwardBlocking(int idx);

    // ---- Break / Remove-from-game (forward-specific, used by mass effect) ---

    /** Breaks P1's forward at {@code idx} (sends to P1's Break Zone). */
    void breakP1Forward(int idx);

    /** Breaks P2's forward at {@code idx} (sends to P2's Break Zone). */
    void breakP2Forward(int idx);

    /** Removes P1's forward at {@code idx} from the game permanently. */
    void removeP1ForwardFromGame(int idx);

    /** Removes P2's forward at {@code idx} from the game permanently. */
    void removeP2ForwardFromGame(int idx);

    // ---- Mass field effects -------------------------------------------------

    /** Action verbs for mass field effects. */
    enum MassAction { BREAK, DULL, FREEZE, DULL_AND_FREEZE, ACTIVATE }

    /**
     * Applies {@code action} to every field card that matches all filters.
     *
     * @param forwards     include Forwards in the sweep
     * @param backups      include Backups in the sweep
     * @param monsters     include Monsters in the sweep
     * @param opponentOnly only affect P2's cards
     * @param selfOnly     only affect P1's cards
     * @param element      optional element filter; {@code null} = any
     * @param costVal      CP cost filter value; {@code -1} = no filter
     * @param costCmp      {@code "less"}, {@code "more"}, or {@code null} for exact
     */
    void applyMassFieldEffect(MassAction action,
            boolean forwards, boolean backups, boolean monsters,
            boolean opponentOnly, boolean selfOnly,
            String element, int costVal, String costCmp);
}

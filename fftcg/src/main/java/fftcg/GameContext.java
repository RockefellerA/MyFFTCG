package fftcg;

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
     * forwards and returns their targets.
     *
     * @param maxCount     maximum number of forwards the player may select
     * @param upTo         if {@code true} the player may confirm with fewer than {@code maxCount}
     * @param opponentOnly if {@code true} only P2's forwards are offered as targets
     * @param condition    optional eligibility filter: {@code "active"}, {@code "dull"}
     *                     {@code "damaged"}, or {@code null} for any forward
     * @return the list of chosen {@link ForwardTarget}s (may be empty if no eligible targets
     *         exist or the player skips)
     */
    /**
     * @param selfOnly  if {@code true} only the active player's (P1's) forwards are eligible
     * @param element   optional element name to restrict targets (e.g. {@code "Earth"}); {@code null} = any
     */
    List<ForwardTarget> selectForwards(int maxCount, boolean upTo, boolean opponentOnly,
            boolean selfOnly, String condition, String element, int costVal, String costCmp);

    // ---- Dull effects -------------------------------------------------------

    /** Dulls P1's forward at {@code idx} and refreshes its slot. */
    void dullP1Forward(int idx);

    /** Dulls P2's forward at {@code idx} and refreshes its slot. */
    void dullP2Forward(int idx);

    /** Freezes P1's forward at {@code idx} (blue tint; skips activation next Active Phase). */
    void freezeP1Forward(int idx);

    /** Freezes P2's forward at {@code idx} (blue tint; skips activation next Active Phase). */
    void freezeP2Forward(int idx);

    // ---- Attack / block state queries ---------------------------------------

    /** Returns {@code true} if P1's forward at {@code idx} is currently declared as an attacker. */
    boolean isP1ForwardAttacking(int idx);

    /** Returns {@code true} if P2's forward at {@code idx} is currently declared as an attacker. */
    boolean isP2ForwardAttacking(int idx);

    /** Returns {@code true} if P1's forward at {@code idx} is currently declared as a blocker. */
    boolean isP1ForwardBlocking(int idx);

    /** Returns {@code true} if P2's forward at {@code idx} is currently declared as a blocker. */
    boolean isP2ForwardBlocking(int idx);

    // ---- Break / Remove-from-game effects -----------------------------------

    /** Breaks P1's forward at {@code idx} (sends to P1's Break Zone). */
    void breakP1Forward(int idx);

    /** Breaks P2's forward at {@code idx} (sends to P2's Break Zone). */
    void breakP2Forward(int idx);

    /**
     * Removes P1's forward at {@code idx} from the game permanently
     * (sends to P1's Removed-From-Game zone, not the Break Zone).
     */
    void removeP1ForwardFromGame(int idx);

    /**
     * Removes P2's forward at {@code idx} from the game permanently
     * (sends to P2's Removed-From-Game zone, not the Break Zone).
     */
    void removeP2ForwardFromGame(int idx);

    // ---- Break-zone selection -----------------------------------------------

    /**
     * Shows a modal dialog letting P1 choose up to {@code maxCount} eligible
     * forwards from a Break Zone and returns their targets.
     *
     * @param opponentZone if {@code true}, selects from P2's Break Zone; otherwise P1's
     * @param condition    optional eligibility filter ({@code "active"}, {@code "dull"},
     *                     {@code "damaged"}, or {@code null} for any)
     * @param element      optional element name to restrict targets; {@code null} = any
     */
    List<ForwardTarget> selectForwardsFromBreakZone(int maxCount, boolean upTo,
            boolean opponentZone, String condition, String element, int costVal, String costCmp);

    // ---- Play from break zone onto field ------------------------------------

    /** Moves P1's forward at break-zone index {@code idx} onto P1's field (no cost paid). */
    void playP1ForwardFromBreakZoneOntoField(int idx);

    /** Moves P2's forward at break-zone index {@code idx} onto the field (no cost paid). */
    void playP2ForwardFromBreakZoneOntoField(int idx);

    // ---- Add from break zone to hand ----------------------------------------

    /** Moves P1's forward at break-zone index {@code idx} to P1's hand. */
    void addP1BreakZoneForwardToHand(int idx);

    /** Moves P2's forward at break-zone index {@code idx} to P1's hand. */
    void addP2BreakZoneForwardToHand(int idx);

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

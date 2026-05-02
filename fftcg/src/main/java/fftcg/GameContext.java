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

    // ---- Dull effects (used by mass-effect; also available individually) ----

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

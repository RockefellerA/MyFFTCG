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

    /** Field state (NORMAL / DULLED / FROZEN / BRAVE_ATTACKED) of P1's forward at {@code idx}. */
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
     * @param condition    optional eligibility filter: {@code "dull"}, {@code "dulled"},
     *                     {@code "damaged"}, or {@code null} for any forward
     * @return the list of chosen {@link ForwardTarget}s (may be empty if no eligible targets
     *         exist or the player skips)
     */
    List<ForwardTarget> selectForwards(int maxCount, boolean upTo, boolean opponentOnly, String condition);

    // ---- Dull effects -------------------------------------------------------

    /** Dulls P1's forward at {@code idx} and refreshes its slot. */
    void dullP1Forward(int idx);

    /** Dulls P2's forward at {@code idx} and refreshes its slot. */
    void dullP2Forward(int idx);
}

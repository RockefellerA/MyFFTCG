package fftcg;

/**
 * Identifies a single forward on the field as a chosen target for an ability.
 *
 * @param isP1 {@code true} if the forward belongs to Player 1; {@code false} for Player 2
 * @param idx  index into the owning player's forward list
 */
public record ForwardTarget(boolean isP1, int idx) {}

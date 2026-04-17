package fftcg;

/**
 * Immutable value object representing a single card in game state.
 * Carries everything needed for display and rules checks.
 */
public record CardData(
        String imageUrl,
        String name,
        String element,
        int    cost,
        String type,
        boolean isLb,
        int     lbCost,
        boolean exBurst
) {
    /** Returns {@code true} for Light or Dark element cards, which cannot be discarded for CP. */
    public boolean isLightOrDark() {
        return "Light".equalsIgnoreCase(element) || "Dark".equalsIgnoreCase(element);
    }

    /** Returns {@code true} if this card's type is Backup. */
    public boolean isBackup() {
        return "Backup".equalsIgnoreCase(type);
    }

    /** Returns {@code true} if this card's type is Forward. */
    public boolean isForward() {
        return "Forward".equalsIgnoreCase(type);
    }
}

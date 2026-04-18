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
    /** Returns {@code true} if any of this card's elements is Light or Dark (cannot be discarded for CP). */
    public boolean isLightOrDark() {
        for (String e : element.split("/"))
            if ("Light".equalsIgnoreCase(e) || "Dark".equalsIgnoreCase(e)) return true;
        return false;
    }

    /** Returns {@code true} if any of this card's elements matches {@code elem} (case-insensitive). */
    public boolean containsElement(String elem) {
        for (String e : element.split("/"))
            if (e.equalsIgnoreCase(elem)) return true;
        return false;
    }

    /** Returns each element of this card as a separate string. */
    public String[] elements() { return element.split("/"); }

    /** Returns {@code true} if this card's type is Backup. */
    public boolean isBackup() {
        return "Backup".equalsIgnoreCase(type);
    }

    /** Returns {@code true} if this card's type is Forward. */
    public boolean isForward() {
        return "Forward".equalsIgnoreCase(type);
    }
}

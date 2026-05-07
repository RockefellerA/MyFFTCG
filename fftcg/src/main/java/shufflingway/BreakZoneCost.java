package shufflingway;

/**
 * A single item in a "put into the Break Zone" ability cost.
 * Either a named card (when {@link #name} is non-empty) or a type-based cost.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code put Summoner into the Break Zone}
 *       → {@code BreakZoneCost("Summoner", 1, "")}</li>
 *   <li>{@code put 1 Backup into the Break Zone}
 *       → {@code BreakZoneCost("", 1, "Backup")}</li>
 *   <li>{@code put 1 Fire Backup into the Break Zone}
 *       → {@code BreakZoneCost("", 1, "Fire Backup")}</li>
 * </ul>
 */
public record BreakZoneCost(
        String name,     // non-empty → named card (e.g. "Summoner"); empty → type-based
        int    count,    // number to break; always 1 for named cards, ≥1 for types
        String cardType  // "Forward", "Backup", "Monster" or compound (e.g. "Fire Backup"); empty for named
) {}

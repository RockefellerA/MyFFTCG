package shufflingway;

import java.util.List;

/**
 * A single parsed Action Ability or Special Ability from a card's text_en.
 *
 * <p>Action abilities follow the format:
 * <pre>  CostTokens: EffectText</pre>
 *
 * <p>Special abilities add a named header and additionally require the player
 * to discard a card with the same name as the activating card:
 * <pre>  [[s]]Name[[/]] CostTokens: EffectText</pre>
 *
 * <h3>Cost token semantics</h3>
 * <ul>
 *   <li>{@link #cpCost} — CP elements the player must pay (element names or {@code ""}
 *       for generic CP).  Excludes {@code Dull} and {@code S} tokens.</li>
 *   <li>{@link #requiresDull} — {@code true} when {@code 《Dull》} appears in the cost
 *       (the card itself must be dulled as part of payment).</li>
 *   <li>{@link #isSpecial} — {@code true} when {@code [[s]]…[[/]]} markup is present
 *       or {@code 《S》} appears in the cost.  Requires discarding a same-name card
 *       from hand in addition to the other costs.</li>
 *   <li>{@link #crystalCost} — number of Crystals the player must spend ({@code 《C》} tokens).</li>
 *   <li>{@link #breakZoneCosts} — one entry per "put X into the Break Zone" cost item;
 *       empty when no such cost is present.</li>
 *   <li>{@link #discardCosts} — one entry per "discard X from hand" cost item;
 *       empty when no such cost is present.</li>
 *   <li>{@link #removeFromGameCosts} — one entry per "remove X from the game" cost item;
 *       empty when no such cost is present.</li>
 * </ul>
 *
 * <p>{@link #effectText} is stored as a raw string for now and will be parsed
 * into discrete effects in a future iteration.
 */
public record ActionAbility(
        String                  abilityName,          // "" for regular abilities; named (e.g. "Mug") for specials
        boolean                 requiresDull,          // 《Dull》 present in cost
        boolean                 isSpecial,             // [[s]]…[[/]] or 《S》 present — requires same-name hand discard
        int                     crystalCost,           // number of Crystals the player must spend (《C》 tokens)
        List<String>            cpCost,                // CP cost elements (element names or "" for generic)
        List<BreakZoneCost>     breakZoneCosts,        // "put X into the Break Zone" costs (may be empty)
        List<DiscardCost>       discardCosts,          // "discard X" hand-card costs (may be empty)
        List<RemoveFromGameCost> removeFromGameCosts,  // "remove X from the game" costs (may be empty)
        List<ReturnToHandCost>   returnToHandCosts,    // "return X to its owner's hand" costs (may be empty)
        String                  effectText             // raw effect text — future work will parse this further
) {
    public ActionAbility {
        cpCost            = List.copyOf(cpCost);
        breakZoneCosts    = List.copyOf(breakZoneCosts);
        discardCosts      = List.copyOf(discardCosts);
        removeFromGameCosts = List.copyOf(removeFromGameCosts);
        returnToHandCosts = List.copyOf(returnToHandCosts);
    }
}

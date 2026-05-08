package shufflingway;

/**
 * A single "discard from hand" payment cost on an Action Ability.
 *
 * <p>Examples and their parsed form:
 * <ul>
 *   <li>{@code discard 1 card}
 *       → {@code DiscardCost(1, null, null, null, null, false)}</li>
 *   <li>{@code discard 1 Water card}
 *       → {@code DiscardCost(1, null, "Water", null, null, false)}</li>
 *   <li>{@code discard 1 Summon}
 *       → {@code DiscardCost(1, null, null, "Summon", null, false)}</li>
 *   <li>{@code discard 1 Card Name Red Mage}
 *       → {@code DiscardCost(1, "Red Mage", null, null, null, false)}</li>
 *   <li>{@code Discard 2 Category VI Characters}
 *       → {@code DiscardCost(2, null, null, "Character", "VI", false)}</li>
 *   <li>{@code Discard 3 cards, each of a different card type}
 *       → {@code DiscardCost(3, null, null, null, null, true)}</li>
 * </ul>
 *
 * @param count            number of cards to discard
 * @param cardName         non-null → must discard a card with this exact name
 * @param element          non-null → must discard a card of this element
 * @param cardType         non-null → "Summon", "Forward", "Backup", "Monster", or "Character"
 * @param category         non-null → must discard a card belonging to this category
 * @param eachDifferentType each discarded card must be a different card type
 */
public record DiscardCost(
        int     count,
        String  cardName,
        String  element,
        String  cardType,
        String  category,
        boolean eachDifferentType
) {}

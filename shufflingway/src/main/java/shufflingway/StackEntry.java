package shufflingway;

/**
 * A single entry on the resolution stack — either a Summon being cast
 * or an Action Ability being activated.
 *
 * @param source  the card that owns this effect
 * @param ability {@code null} for a Summon; the activated {@link ActionAbility} otherwise
 * @param isP1    {@code true} when the effect was triggered by Player 1
 */
public record StackEntry(CardData source, ActionAbility ability, boolean isP1) {

    public boolean isSummon() { return ability == null; }

    /** The raw effect text that {@link ActionResolver#parse} will run. */
    public String effectText() {
        return ability != null ? ability.effectText() : source.summonEffect();
    }
}

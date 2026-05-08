package shufflingway;

/**
 * A single entry on the resolution stack — either a Summon being cast
 * or an Action Ability being activated.
 *
 * @param source  the card that owns this effect
 * @param ability {@code null} for a Summon; the activated {@link ActionAbility} otherwise
 * @param isP1    {@code true} when the effect was triggered by Player 1
 * @param xValue  the amount of CP paid into {@code 《X》}; {@code 0} when the ability has no X cost
 */
public record StackEntry(CardData source, ActionAbility ability, boolean isP1, int xValue) {

    /** Convenience constructor for summons and non-X abilities (xValue defaults to 0). */
    public StackEntry(CardData source, ActionAbility ability, boolean isP1) {
        this(source, ability, isP1, 0);
    }

    public boolean isSummon() { return ability == null; }

    /** The raw effect text that {@link ActionResolver#parse} will run. */
    public String effectText() {
        return ability != null ? ability.effectText() : source.summonEffect();
    }
}

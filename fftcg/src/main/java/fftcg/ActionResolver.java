package fftcg;

/**
 * Resolves Action and Special Ability effect text against game state.
 *
 * Effect parsing is a work in progress.  At present this class serves
 * as a stub that logs the activation; individual effect handlers will
 * be added here as they are implemented.
 */
public class ActionResolver {

    /**
     * Resolves an Action Ability that has already been paid for.
     *
     * @param ability   the ability being activated
     * @param source    the card that used the ability
     * @param gameState current game state (will be mutated by effect handlers)
     */
    public static void resolve(ActionAbility ability, CardData source, GameState gameState) {
        // TODO: parse ability.effectText() into discrete effect instructions and execute them
        System.out.println("[ActionResolver] '" + source.name() + "' activated: " + ability.effectText());
    }
}

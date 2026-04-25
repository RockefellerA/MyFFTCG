package fftcg;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Action Ability effect text into executable game effects and resolves
 * them against the live game state via a {@link GameContext}.
 *
 * <h3>Adding new effect types</h3>
 * <ol>
 *   <li>Add a {@code static final Pattern} for the new text pattern.</li>
 *   <li>Add a {@code tryParse*} method that returns a {@code Consumer<GameContext>}
 *       (or {@code null} if the text does not match).</li>
 *   <li>Call it from {@link #parse(String)}.</li>
 * </ol>
 */
public class ActionResolver {

    // -------------------------------------------------------------------------
    // Patterns
    // -------------------------------------------------------------------------

    /**
     * Matches: "Deal X damage to all [the] [condition] Forwards[.] [your opponent controls]"
     * <ul>
     *   <li>Group {@code amount}    — numeric damage value</li>
     *   <li>Group {@code condition} — optional "damaged" or "dull/dulled"</li>
     *   <li>Group {@code opponent}  — present when "your opponent controls" appears</li>
     * </ul>
     */
    private static final Pattern DEAL_DAMAGE_TO_FORWARDS = Pattern.compile(
        "(?i)Deal\\s+(?<amount>\\d+)\\s+damage\\s+to\\s+all(?:\\s+the)?\\s+" +
        "(?:(?<condition>damaged|dull(?:ed)?)\\s+)?" +
        "Forwards?(?:\\s+(?<opponent>opponent\\s+controls))?"
    );

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Attempts to parse {@code effectText} into a ready-to-execute
     * {@link Consumer}{@code <GameContext>}.
     *
     * @return the effect consumer, or {@code null} if the text is not yet supported
     */
    public static Consumer<GameContext> parse(String effectText) {
        Consumer<GameContext> result;

        result = tryParseDealDamageToForwards(effectText);
        if (result != null) return result;

        // TODO: add more effect parsers here as they are implemented

        return null;
    }

    /**
     * Resolves an activated Action Ability:
     * <ol>
     *   <li>Logs the ability being pushed to the stack.</li>
     *   <li>AI (P2) automatically passes priority (no response implemented yet).</li>
     *   <li>Pops and executes the effect; logs an info message if unparsed.</li>
     * </ol>
     *
     * @param ability   the ability being activated
     * @param source    the card that used the ability
     * @param gameState current game state
     * @param ctx       live context for applying effects to the field
     */
    public static void resolve(ActionAbility ability, CardData source,
            GameState gameState, GameContext ctx) {
        ctx.logEntry("[Stack] \"" + source.name() + "\" → " + ability.effectText());
        ctx.logEntry("[Stack] P2 passes — resolving");

        Consumer<GameContext> effect = parse(ability.effectText());
        if (effect != null) {
            effect.accept(ctx);
        } else {
            ctx.logEntry("[ActionResolver] Effect not yet implemented: " + ability.effectText());
        }
    }

    // -------------------------------------------------------------------------
    // Effect parsers
    // -------------------------------------------------------------------------

    /**
     * Parses "Deal X damage to all [condition] Forwards [your opponent controls]".
     *
     * <ul>
     *   <li>No condition — all Forwards (P1 and P2, or opponent only if stated)</li>
     *   <li>condition=dull/dulled — only Dulled Forwards</li>
     *   <li>condition=damaged — only Forwards that have already taken damage</li>
     * </ul>
     *
     * Targets are collected before damage is applied.  Forwards are damaged in
     * reverse-index order so that breaks (which shift the list) do not corrupt
     * subsequent indices.
     */
    private static Consumer<GameContext> tryParseDealDamageToForwards(String text) {
        Matcher m = DEAL_DAMAGE_TO_FORWARDS.matcher(text);
        if (!m.find()) return null;

        int    damage       = Integer.parseInt(m.group("amount"));
        String condition    = m.group("condition");   // nullable
        boolean opponentOnly = m.group("opponent") != null;

        return ctx -> {
            String condLabel = condition != null ? (condition + " ") : "";
            String scopeLabel = opponentOnly ? "P2's " : "all ";
            ctx.logEntry("Effect: Deal " + damage + " damage to "
                    + scopeLabel + condLabel + "Forwards");

            // --- P2 forwards (always included) ---
            List<Integer> p2Targets = new ArrayList<>();
            for (int i = 0; i < ctx.p2ForwardCount(); i++) {
                if (meetsCondition(ctx.p2ForwardState(i), ctx.p2ForwardCurrentDamage(i), condition))
                    p2Targets.add(i);
            }
            for (int i = p2Targets.size() - 1; i >= 0; i--) {
                int idx = p2Targets.get(i);
                if (idx < ctx.p2ForwardCount())
                    ctx.damageP2Forward(idx, damage);
            }

            // --- P1 forwards (only when effect is not opponent-only) ---
            if (!opponentOnly) {
                List<Integer> p1Targets = new ArrayList<>();
                for (int i = 0; i < ctx.p1ForwardCount(); i++) {
                    if (meetsCondition(ctx.p1ForwardState(i), ctx.p1ForwardCurrentDamage(i), condition))
                        p1Targets.add(i);
                }
                for (int i = p1Targets.size() - 1; i >= 0; i--) {
                    int idx = p1Targets.get(i);
                    if (idx < ctx.p1ForwardCount())
                        ctx.damageP1Forward(idx, damage);
                }
            }
        };
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if a forward with the given state and accumulated
     * damage satisfies {@code condition}.
     *
     * @param condition {@code "dull"}, {@code "dulled"}, {@code "damaged"},
     *                  or {@code null} (matches everything)
     */
    private static boolean meetsCondition(CardState state, int currentDamage,
            String condition) {
        if (condition == null) return true;
        return switch (condition.toLowerCase()) {
            case "dull", "dulled" -> state == CardState.DULLED;
            case "damaged"        -> currentDamage > 0;
            default               -> true;
        };
    }
}

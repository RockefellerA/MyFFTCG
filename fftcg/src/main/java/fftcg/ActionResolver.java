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
     * Matches the "Choose" targeted effect header:
     * "Choose [up to] N [condition] Forward[s] [opponent controls] [separator] followup"
     * <ul>
     *   <li>Group {@code upto}      — present when "up to" precedes the count</li>
     *   <li>Group {@code count}     — number of forwards to choose</li>
     *   <li>Group {@code condition} — optional: "dull", "dulled", or "damaged"</li>
     *   <li>Group {@code opponent}  — present when "opponent controls" appears</li>
     *   <li>Group {@code followup}  — the action to apply to chosen targets</li>
     * </ul>
     */
    private static final Pattern CHOOSE_FORWARDS_PATTERN = Pattern.compile(
        "(?i)Choose\\s+(?<upto>up\\s+to\\s+)?(?<count>\\d+)\\s+" +
        "(?:(?<condition>dull(?:ed)?|damaged)\\s+)?" +
        "Forwards?" +
        "(?:\\s+(?<opponent>(?:your\\s+)?opponent\\s+controls))?" +
        "(?:[.]\\s*|\\s+and\\s+|,\\s*)" +
        "(?<followup>.+)"
    );

    /** Matches "deal [it|them] N damage" or "deal N damage to [it|them]". */
    private static final Pattern FOLLOWUP_DAMAGE = Pattern.compile(
        "(?i)deal(?:\\s+(?:it|them))?\\s+(\\d+)\\s+damage(?:\\s+to\\s+(?:it|them))?"
    );

    /** Matches "dull it" or "dull them". */
    private static final Pattern FOLLOWUP_DULL = Pattern.compile(
        "(?i)dull\\s+(?:it|them)"
    );

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

        result = tryParseChooseForwards(effectText);
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

    // -------------------------------------------------------------------------
    // Choose-forwards effect parser
    // -------------------------------------------------------------------------

    /**
     * Parses "Choose [up to] N [condition] Forward[s] [opponent controls] [sep] followup".
     *
     * <p>Supported followup actions:
     * <ul>
     *   <li>"Deal [it|them] N damage" / "Deal N damage to [it|them]" — damages each chosen forward</li>
     *   <li>"Dull it" / "Dull them" — dulls each chosen forward</li>
     * </ul>
     */
    private static Consumer<GameContext> tryParseChooseForwards(String text) {
        Matcher m = CHOOSE_FORWARDS_PATTERN.matcher(text);
        if (!m.find()) return null;

        boolean upTo       = m.group("upto") != null;
        int     maxCount   = Integer.parseInt(m.group("count"));
        String  condition  = m.group("condition");   // nullable
        boolean opponentOnly = m.group("opponent") != null;
        String  followup   = m.group("followup").trim();

        // --- Damage followup ---
        Matcher dmgM = FOLLOWUP_DAMAGE.matcher(followup);
        if (dmgM.find()) {
            int damage = Integer.parseInt(dmgM.group(1));
            return ctx -> {
                ctx.logEntry("Choose " + (upTo ? "up to " : "") + maxCount
                        + (condition != null ? " " + condition : "") + " Forward(s)"
                        + (opponentOnly ? " (opponent)" : "") + " — Deal " + damage + " damage");
                List<ForwardTarget> targets =
                        ctx.selectForwards(maxCount, upTo, opponentOnly, condition);
                // Apply damage in reverse-index order within each player to keep indices stable
                targets.stream().filter(ForwardTarget::isP1)
                        .sorted((a, b) -> Integer.compare(b.idx(), a.idx()))
                        .forEach(t -> ctx.damageP1Forward(t.idx(), damage));
                targets.stream().filter(t -> !t.isP1())
                        .sorted((a, b) -> Integer.compare(b.idx(), a.idx()))
                        .forEach(t -> ctx.damageP2Forward(t.idx(), damage));
            };
        }

        // --- Dull followup ---
        if (FOLLOWUP_DULL.matcher(followup).find()) {
            return ctx -> {
                ctx.logEntry("Choose " + (upTo ? "up to " : "") + maxCount
                        + (condition != null ? " " + condition : "") + " Forward(s)"
                        + (opponentOnly ? " (opponent)" : "") + " — Dull");
                List<ForwardTarget> targets =
                        ctx.selectForwards(maxCount, upTo, opponentOnly, condition);
                targets.stream().filter(ForwardTarget::isP1)
                        .sorted((a, b) -> Integer.compare(b.idx(), a.idx()))
                        .forEach(t -> ctx.dullP1Forward(t.idx()));
                targets.stream().filter(t -> !t.isP1())
                        .sorted((a, b) -> Integer.compare(b.idx(), a.idx()))
                        .forEach(t -> ctx.dullP2Forward(t.idx()));
            };
        }

        // Recognised "Choose" header but followup not yet implemented
        return ctx -> ctx.logEntry(
                "[ActionResolver] Choose effect — followup not yet implemented: " + followup);
    }
}

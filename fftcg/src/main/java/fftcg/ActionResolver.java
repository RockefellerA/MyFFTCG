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
     * "Choose [up to] N [condition] [element] Forward[s] [of cost X [or less|more]] [control] [zone]
     *  [separator] followup"
     * <ul>
     *   <li>Group {@code upto}      — present when "up to" precedes the count</li>
     *   <li>Group {@code count}     — number of forwards to choose</li>
     *   <li>Group {@code condition} — optional: "dull", "damaged", "attacking", "blocking", or "active"</li>
     *   <li>Group {@code element}   — optional element name, e.g. "Fire", "Earth"</li>
     *   <li>Group {@code cost}      — optional CP cost value, e.g. "3" in "of cost 3 or less"</li>
     *   <li>Group {@code costcmp}   — optional comparison: "less" or "more" (absent = exact match)</li>
     *   <li>Group {@code control}   — optional: "opponent controls", "your opponent controls",
     *                                 or "you control"</li>
     *   <li>Group {@code zone}      — optional zone, e.g. "in your Break Zone" or
     *                                 "in your opponent's Break Zone"</li>
     *   <li>Group {@code followup}  — the action to apply to chosen targets</li>
     * </ul>
     */
    private static final Pattern CHOOSE_FORWARDS_PATTERN = Pattern.compile(
        "(?i)Choose\\s+(?<upto>up\\s+to\\s+)?(?<count>\\d+)\\s+" +
        "(?:(?<condition>dull|damaged|attacking|blocking|active)\\s+)?" +
        "(?:(?<element>Fire|Ice|Wind|Earth|Lightning|Water|Light|Dark)\\s+)?" +
        "Forwards?" +
        "(?:\\s+of\\s+cost\\s+(?<cost>\\d+)(?:\\s+or\\s+(?<costcmp>less|more))?)?" +
        "(?:\\s+(?<control>(?:your\\s+)?opponent\\s+controls|you\\s+control))?" +
        "(?:\\s+(?<zone>in\\s+your(?:\\s+opponent(?:'s)?)?\\s+Break\\s+Zone))?" +
        "(?:[.]\\s*|\\s+and\\s+|,\\s*)" +
        "(?<followup>.+)"
    );

    /** Matches "deal it/them N damage". */
    private static final Pattern FOLLOWUP_DAMAGE = Pattern.compile(
        "(?i)deal\\s+(?:it|them)\\s+(\\d+)\\s+damage"
    );

    /** Matches "dull it" or "dull them". */
    private static final Pattern FOLLOWUP_DULL = Pattern.compile(
        "(?i)dull\\s+(?:it|them)"
    );

    /** Matches "freeze it" or "freeze them". */
    private static final Pattern FOLLOWUP_FREEZE = Pattern.compile(
        "(?i)freeze\\s+(?:it|them)"
    );

    /** Matches "dull it/them and freeze it/them". */
    private static final Pattern FOLLOWUP_DULL_AND_FREEZE = Pattern.compile(
        "(?i)dull\\s+(?:it|them)\\s+and\\s+freeze\\s+(?:it|them)"
    );

    /** Matches "Break it" or "Break them". */
    private static final Pattern FOLLOWUP_BREAK = Pattern.compile(
        "(?i)Break\\s+(?:it|them)"
    );

    /** Matches "Remove it/them from the game". */
    private static final Pattern FOLLOWUP_REMOVE_FROM_GAME = Pattern.compile(
        "(?i)Remove\\s+(?:it|them)\\s+from\\s+(?:the\\s+)?game"
    );

    /** Matches "Play it onto the field" or "Play them onto the field". */
    private static final Pattern FOLLOWUP_PLAY_ONTO_FIELD = Pattern.compile(
        "(?i)Play\\s+(?:it|them)\\s+onto\\s+(?:the\\s+)?field"
    );

    /** Matches "Add it to your hand" or "Add them to your hand". */
    private static final Pattern FOLLOWUP_ADD_TO_HAND = Pattern.compile(
        "(?i)Add\\s+(?:it|them)\\s+to\\s+your\\s+hand"
    );

    /**
     * Matches: "Deal X damage to all [the] [condition] Forwards[.] [opponent controls]"
     * <ul>
     *   <li>Group {@code amount}    — numeric damage value</li>
     *   <li>Group {@code condition} — optional "damaged" or "dull"</li>
     *   <li>Group {@code opponent}  — present when "opponent controls" appears</li>
     * </ul>
     */
    private static final Pattern DEAL_DAMAGE_TO_FORWARDS = Pattern.compile(
        "(?i)Deal\\s+(?<amount>\\d+)\\s+damage\\s+to\\s+all(?:\\s+the)?\\s+" +
        "(?:(?<condition>damaged|dull|attacking|blocking)\\s+)?" +
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
     *   <li>condition=dull — only Dulled Forwards</li>
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
                if (meetsCondition(ctx.p2ForwardState(i), ctx.p2ForwardCurrentDamage(i),
                        ctx.isP2ForwardAttacking(i), ctx.isP2ForwardBlocking(i), condition))
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
                    if (meetsCondition(ctx.p1ForwardState(i), ctx.p1ForwardCurrentDamage(i),
                            ctx.isP1ForwardAttacking(i), ctx.isP1ForwardBlocking(i), condition))
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
     * Returns {@code true} if a forward satisfies {@code condition}.
     *
     * @param condition {@code "active"}, {@code "dull"}, {@code "damaged"},
     *                  {@code "attacking"}, {@code "blocking"}, or {@code null} (any)
     */
    private static boolean meetsCondition(CardState state, int currentDamage,
            boolean isAttacking, boolean isBlocking, String condition) {
        if (condition == null) return true;
        return switch (condition.toLowerCase()) {
            case "active"         -> state == CardState.ACTIVE;
            case "dull"           -> state == CardState.DULL;
            case "damaged"        -> currentDamage > 0;
            case "attacking"      -> isAttacking;
            case "blocking"       -> isBlocking;
            default               -> true;
        };
    }

    // -------------------------------------------------------------------------
    // Choose-forwards effect parser
    // -------------------------------------------------------------------------

    /**
     * Parses "Choose [up to] N [condition] Forward[s] [control] [zone] [sep] followup".
     *
     * <p>Supported followup actions:
     * <ul>
     *   <li>"Deal [it|them] N damage"     — damages each chosen forward</li>
     *   <li>"Dull it/them"                — dulls each chosen forward</li>
     *   <li>"Freeze it/them"              — freezes each chosen forward</li>
     *   <li>"Dull it/them and freeze…"    — dulls and freezes each chosen forward</li>
     *   <li>"Break it/them"               — breaks each chosen forward</li>
     *   <li>"Remove it/them from the game"— removes each chosen forward from the game</li>
     *   <li>"Play it/them onto the field" — moves chosen forwards from their zone onto the field</li>
     *   <li>"Add it/them to your hand"    — moves chosen forwards from their zone to P1's hand</li>
     * </ul>
     */
    private static Consumer<GameContext> tryParseChooseForwards(String text) {
        Matcher m = CHOOSE_FORWARDS_PATTERN.matcher(text);
        if (!m.find()) return null;

        boolean upTo         = m.group("upto") != null;
        int     maxCount     = Integer.parseInt(m.group("count"));
        String  condition    = m.group("condition");   // nullable
        String  element      = m.group("element");     // nullable — e.g. "Earth", "Fire"
        String  costStr      = m.group("cost");        // nullable — numeric cost value
        String  costCmp      = m.group("costcmp");     // nullable — "less" or "more"
        int     costVal      = costStr != null ? Integer.parseInt(costStr) : -1;
        String  control      = m.group("control");     // nullable — "opponent controls" / "you control"
        boolean opponentOnly = control != null && !control.equalsIgnoreCase("you control");
        boolean selfOnly     = "you control".equalsIgnoreCase(control);
        String  zone         = m.group("zone");        // nullable — "in your Break Zone" etc.
        boolean opponentZone = zone != null && zone.toLowerCase().contains("opponent");
        String  followup     = m.group("followup").trim();

        // --- Damage followup ---
        Matcher dmgM = FOLLOWUP_DAMAGE.matcher(followup);
        if (dmgM.find()) {
            int damage = Integer.parseInt(dmgM.group(1));
            return ctx -> {
                ctx.logEntry("Choose " + (upTo ? "up to " : "") + maxCount
                        + (condition != null ? " " + condition : "") + " Forward(s)"
                        + (opponentOnly ? " (opponent)" : "") + " — Deal " + damage + " damage");
                List<ForwardTarget> targets = selectTargets(
                        ctx, maxCount, upTo, opponentOnly, selfOnly, condition, element,
                        zone, opponentZone, costVal, costCmp);
                targets.stream().filter(ForwardTarget::isP1)
                        .sorted((a, b) -> Integer.compare(b.idx(), a.idx()))
                        .forEach(t -> ctx.damageP1Forward(t.idx(), damage));
                targets.stream().filter(t -> !t.isP1())
                        .sorted((a, b) -> Integer.compare(b.idx(), a.idx()))
                        .forEach(t -> ctx.damageP2Forward(t.idx(), damage));
            };
        }

        // --- Dull followup ---
        if (FOLLOWUP_DULL.matcher(followup).find()
                && !FOLLOWUP_DULL_AND_FREEZE.matcher(followup).find()) {
            return ctx -> {
                ctx.logEntry("Choose " + (upTo ? "up to " : "") + maxCount
                        + (condition != null ? " " + condition : "") + " Forward(s)"
                        + (opponentOnly ? " (opponent)" : "") + " — Dull");
                List<ForwardTarget> targets = selectTargets(
                        ctx, maxCount, upTo, opponentOnly, selfOnly, condition, element,
                        zone, opponentZone, costVal, costCmp);
                targets.stream().filter(ForwardTarget::isP1)
                        .sorted((a, b) -> Integer.compare(b.idx(), a.idx()))
                        .forEach(t -> ctx.dullP1Forward(t.idx()));
                targets.stream().filter(t -> !t.isP1())
                        .sorted((a, b) -> Integer.compare(b.idx(), a.idx()))
                        .forEach(t -> ctx.dullP2Forward(t.idx()));
            };
        }

        // --- Dull + Freeze followup ---
        if (FOLLOWUP_DULL_AND_FREEZE.matcher(followup).find()) {
            return ctx -> {
                ctx.logEntry("Choose " + (upTo ? "up to " : "") + maxCount
                        + (condition != null ? " " + condition : "") + " Forward(s)"
                        + (opponentOnly ? " (opponent)" : "") + " — Dull & Freeze");
                List<ForwardTarget> targets = selectTargets(
                        ctx, maxCount, upTo, opponentOnly, selfOnly, condition, element,
                        zone, opponentZone, costVal, costCmp);
                targets.stream().filter(ForwardTarget::isP1)
                        .sorted((a, b) -> Integer.compare(b.idx(), a.idx()))
                        .forEach(t -> { ctx.dullP1Forward(t.idx()); ctx.freezeP1Forward(t.idx()); });
                targets.stream().filter(t -> !t.isP1())
                        .sorted((a, b) -> Integer.compare(b.idx(), a.idx()))
                        .forEach(t -> { ctx.dullP2Forward(t.idx()); ctx.freezeP2Forward(t.idx()); });
            };
        }

        // --- Freeze followup ---
        if (FOLLOWUP_FREEZE.matcher(followup).find()) {
            return ctx -> {
                ctx.logEntry("Choose " + (upTo ? "up to " : "") + maxCount
                        + (condition != null ? " " + condition : "") + " Forward(s)"
                        + (opponentOnly ? " (opponent)" : "") + " — Freeze");
                List<ForwardTarget> targets = selectTargets(
                        ctx, maxCount, upTo, opponentOnly, selfOnly, condition, element,
                        zone, opponentZone, costVal, costCmp);
                targets.stream().filter(ForwardTarget::isP1)
                        .sorted((a, b) -> Integer.compare(b.idx(), a.idx()))
                        .forEach(t -> ctx.freezeP1Forward(t.idx()));
                targets.stream().filter(t -> !t.isP1())
                        .sorted((a, b) -> Integer.compare(b.idx(), a.idx()))
                        .forEach(t -> ctx.freezeP2Forward(t.idx()));
            };
        }

        // --- Break followup ---
        if (FOLLOWUP_BREAK.matcher(followup).find()) {
            return ctx -> {
                ctx.logEntry("Choose " + (upTo ? "up to " : "") + maxCount
                        + (condition != null ? " " + condition : "") + " Forward(s)"
                        + (opponentOnly ? " (opponent)" : "") + " — Break");
                List<ForwardTarget> targets = selectTargets(
                        ctx, maxCount, upTo, opponentOnly, selfOnly, condition, element,
                        zone, opponentZone, costVal, costCmp);
                targets.stream().filter(ForwardTarget::isP1)
                        .sorted((a, b) -> Integer.compare(b.idx(), a.idx()))
                        .forEach(t -> ctx.breakP1Forward(t.idx()));
                targets.stream().filter(t -> !t.isP1())
                        .sorted((a, b) -> Integer.compare(b.idx(), a.idx()))
                        .forEach(t -> ctx.breakP2Forward(t.idx()));
            };
        }

        // --- Remove from game followup ---
        if (FOLLOWUP_REMOVE_FROM_GAME.matcher(followup).find()) {
            return ctx -> {
                ctx.logEntry("Choose " + (upTo ? "up to " : "") + maxCount
                        + (condition != null ? " " + condition : "") + " Forward(s)"
                        + (opponentOnly ? " (opponent)" : "") + " — Remove From Game");
                List<ForwardTarget> targets = selectTargets(
                        ctx, maxCount, upTo, opponentOnly, selfOnly, condition, element,
                        zone, opponentZone, costVal, costCmp);
                targets.stream().filter(ForwardTarget::isP1)
                        .sorted((a, b) -> Integer.compare(b.idx(), a.idx()))
                        .forEach(t -> ctx.removeP1ForwardFromGame(t.idx()));
                targets.stream().filter(t -> !t.isP1())
                        .sorted((a, b) -> Integer.compare(b.idx(), a.idx()))
                        .forEach(t -> ctx.removeP2ForwardFromGame(t.idx()));
            };
        }

        // --- Play onto field followup ---
        if (FOLLOWUP_PLAY_ONTO_FIELD.matcher(followup).find()) {
            String zoneLabel = zone != null
                    ? " in " + (opponentZone ? "opponent's" : "your") + " Break Zone" : "";
            return ctx -> {
                ctx.logEntry("Choose " + (upTo ? "up to " : "") + maxCount
                        + (condition != null ? " " + condition : "") + " Forward(s)"
                        + zoneLabel + " — Play onto Field");
                List<ForwardTarget> targets = selectTargets(
                        ctx, maxCount, upTo, opponentOnly, selfOnly, condition, element,
                        zone, opponentZone, costVal, costCmp);
                targets.stream().filter(ForwardTarget::isP1)
                        .sorted((a, b) -> Integer.compare(b.idx(), a.idx()))
                        .forEach(t -> ctx.playP1ForwardFromBreakZoneOntoField(t.idx()));
                targets.stream().filter(t -> !t.isP1())
                        .sorted((a, b) -> Integer.compare(b.idx(), a.idx()))
                        .forEach(t -> ctx.playP2ForwardFromBreakZoneOntoField(t.idx()));
            };
        }

        // --- Add to hand followup ---
        if (FOLLOWUP_ADD_TO_HAND.matcher(followup).find()) {
            String zoneLabel = zone != null
                    ? " in " + (opponentZone ? "opponent's" : "your") + " Break Zone" : "";
            return ctx -> {
                ctx.logEntry("Choose " + (upTo ? "up to " : "") + maxCount
                        + (condition != null ? " " + condition : "") + " Forward(s)"
                        + zoneLabel + " — Add to Hand");
                List<ForwardTarget> targets = selectTargets(
                        ctx, maxCount, upTo, opponentOnly, selfOnly, condition, element,
                        zone, opponentZone, costVal, costCmp);
                targets.stream().filter(ForwardTarget::isP1)
                        .sorted((a, b) -> Integer.compare(b.idx(), a.idx()))
                        .forEach(t -> ctx.addP1BreakZoneForwardToHand(t.idx()));
                targets.stream().filter(t -> !t.isP1())
                        .sorted((a, b) -> Integer.compare(b.idx(), a.idx()))
                        .forEach(t -> ctx.addP2BreakZoneForwardToHand(t.idx()));
            };
        }

        // Recognised "Choose" header but followup not yet implemented
        return ctx -> ctx.logEntry(
                "[ActionResolver] Choose effect — followup not yet implemented: " + followup);
    }

    /**
     * Routes target selection to either the field or a Break Zone depending on
     * whether {@code zone} is non-null, and forwards the cost constraint.
     */
    private static List<ForwardTarget> selectTargets(GameContext ctx,
            int maxCount, boolean upTo, boolean opponentOnly, boolean selfOnly,
            String condition, String element, String zone, boolean opponentZone,
            int costVal, String costCmp) {
        return zone != null
                ? ctx.selectForwardsFromBreakZone(maxCount, upTo, opponentZone, condition, element, costVal, costCmp)
                : ctx.selectForwards(maxCount, upTo, opponentOnly, selfOnly, condition, element, costVal, costCmp);
    }
}

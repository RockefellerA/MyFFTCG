package shufflingway;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;

public class CardParsingTest {

    @Test
    void reportCardParsingCoverage() throws Exception {
        File dbFile = new File("fftcg_cards.db");
        if (!dbFile.exists()) {
            System.out.println("[CardParsingTest] fftcg_cards.db not found — skipping.");
            return;
        }

        int totalCards      = 0;
        int noAbilities     = 0;
        int fullyParsed     = 0;
        int partiallyParsed = 0;
        int noneParsed      = 0;

        String exampleFully   = null;
        String examplePartial = null;
        String exampleNone    = null;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             Statement  stmt = conn.createStatement();
             ResultSet  rs   = stmt.executeQuery(
                     "SELECT name_en, element, cost, power, type_en, ex_burst, multicard, " +
                     "limit_break, lb_cost, image_url, text_en, job_en, category_1, category_2 " +
                     "FROM cards ORDER BY serial")) {

            while (rs.next()) {
                totalCards++;
                String textEn = rs.getString("text_en");

                if (textEn == null || textEn.isBlank()) {
                    noAbilities++;
                    continue;
                }

                List<ActionAbility> abilities = CardData.parseActionAbilities(textEn);
                if (abilities.isEmpty()) {
                    noAbilities++;
                    continue;
                }

                CardData source = new CardData(
                        rs.getString("image_url"),
                        rs.getString("name_en"),
                        rs.getString("element"),
                        rs.getInt("cost"),
                        rs.getInt("power"),
                        rs.getString("type_en"),
                        rs.getInt("limit_break") != 0,
                        rs.getObject("lb_cost") != null ? rs.getInt("lb_cost") : 0,
                        rs.getInt("ex_burst") != 0,
                        rs.getInt("multicard") != 0,
                        CardData.parseTraits(textEn),
                        CardData.parseWarpValue(textEn),
                        CardData.parseWarpCost(textEn),
                        CardData.parsePrimingTarget(textEn),
                        CardData.parsePrimingCost(textEn),
                        abilities, rs.getString("job_en"),
                        rs.getString("category_1"), rs.getString("category_2"), textEn);

                int parsed = 0;
                for (ActionAbility ab : abilities) {
                    if (ActionResolver.parse(ab.effectText(), source) != null) parsed++;
                }

                if (parsed == abilities.size()) {
                    fullyParsed++;
                    if (exampleFully == null)
                        exampleFully = formatExample(source.name(), abilities, source, true);
                } else if (parsed > 0) {
                    partiallyParsed++;
                    if (examplePartial == null)
                        examplePartial = formatExample(source.name(), abilities, source, false);
                } else {
                    noneParsed++;
                    if (exampleNone == null)
                        exampleNone = formatExample(source.name(), abilities, source, false);
                }
            }
        }

        int withAbilities = fullyParsed + partiallyParsed + noneParsed;
        System.out.printf("%n=== Card Parsing Coverage ===%n");
        System.out.printf("Total cards:          %5d%n", totalCards);
        System.out.printf("No action abilities:  %5d%n", noAbilities);
        System.out.printf("With action abilities:%5d%n", withAbilities);
        System.out.printf("  Fully parsed:       %5d  (%.1f%%)%n", fullyParsed,      pct(fullyParsed,      withAbilities));
        System.out.printf("  Partially parsed:   %5d  (%.1f%%)%n", partiallyParsed,  pct(partiallyParsed,  withAbilities));
        System.out.printf("  None parsed:        %5d  (%.1f%%)%n", noneParsed,       pct(noneParsed,       withAbilities));
        System.out.println();
        System.out.printf("--- Example: Fully parsed ---%n%s%n", exampleFully   != null ? exampleFully   : "(none)");
        System.out.printf("--- Example: Partially parsed ---%n%s%n", examplePartial != null ? examplePartial : "(none)");
        System.out.printf("--- Example: Unrecognized ---%n%s%n",    exampleNone    != null ? exampleNone    : "(none)");
    }

    private static String formatExample(String name, List<ActionAbility> abilities, CardData source, boolean allParsed) {
        StringBuilder sb = new StringBuilder();
        sb.append("  Card: ").append(name).append('\n');
        for (ActionAbility ab : abilities) {
            boolean ok = ActionResolver.parse(ab.effectText(), source) != null;
            sb.append("  [").append(ok ? "OK" : "--").append("] ").append(ab.effectText()).append('\n');
        }
        return sb.toString();
    }

    private static double pct(int n, int total) {
        return total == 0 ? 0.0 : n * 100.0 / total;
    }
}

package scraper;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite persistence layer for scraped FFTCG card metadata.
 *
 * Usage:
 *   try (CardDatabase db = new CardDatabase("fftcg_cards.db")) {
 *       db.saveCards(scraper.scrapeSet("1"));
 *   }
 *
 * The database file is created automatically on first use.
 * image_data (BLOB) is optional — only populated if you explicitly call saveImageData().
 */
public class CardDatabase implements AutoCloseable {

    private final Connection conn;

    public CardDatabase(String dbPath) throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
        }
        createSchema();
    }

    // -------------------------------------------------------------------------
    // Schema
    // -------------------------------------------------------------------------

    private void createSchema() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS cards (
                    serial       TEXT PRIMARY KEY,
                    name_en      TEXT,
                    type_en      TEXT,
                    element      TEXT,
                    cost         INTEGER,
                    power        INTEGER,
                    rarity       TEXT,
                    job_en       TEXT,
                    category_1   TEXT,
                    category_2   TEXT,
                    ex_burst     INTEGER NOT NULL DEFAULT 0,
                    multicard    INTEGER NOT NULL DEFAULT 0,
                    text_en      TEXT,
                    thumb_name   TEXT,
                    image_url    TEXT,
                    image_data   BLOB,
                    set_number   TEXT,
                    limit_break  INTEGER NOT NULL DEFAULT 0,
                    lb_cost      INTEGER
                )
                """);
            s.execute("CREATE INDEX IF NOT EXISTS idx_cards_set     ON cards(set_number)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_cards_type    ON cards(type_en)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_cards_element ON cards(element)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_cards_rarity  ON cards(rarity)");

            // Migration: add columns to existing databases that pre-date this schema change
            try { s.execute("ALTER TABLE cards ADD COLUMN image_data BLOB"); } catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE cards ADD COLUMN limit_break INTEGER NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE cards ADD COLUMN lb_cost INTEGER"); } catch (SQLException ignored) {}

            // Populate limit_break / lb_cost for any rows not yet processed
            s.execute("""
                UPDATE cards SET
                    limit_break = 1,
                    lb_cost     = CAST(SUBSTR(text_en, INSTR(text_en, 'Limit Break -- ') + 15) AS INTEGER)
                WHERE text_en LIKE '%Limit Break -- %' AND lb_cost IS NULL
                """);
        }
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /** Inserts or updates a single card (upsert on serial). Skips cards with no serial. */
    public void saveCard(ScrapedCard card) throws SQLException {
        if (card.serial == null || card.serial.isBlank()) return;
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO cards (
                    serial, name_en, type_en, element, cost, power, rarity,
                    job_en, category_1, category_2, ex_burst, multicard,
                    text_en, thumb_name, image_url, set_number,
                    limit_break, lb_cost
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(serial) DO UPDATE SET
                    name_en     = excluded.name_en,
                    type_en     = excluded.type_en,
                    element     = excluded.element,
                    cost        = excluded.cost,
                    power       = excluded.power,
                    rarity      = excluded.rarity,
                    job_en      = excluded.job_en,
                    category_1  = excluded.category_1,
                    category_2  = excluded.category_2,
                    ex_burst    = excluded.ex_burst,
                    multicard   = excluded.multicard,
                    text_en     = excluded.text_en,
                    thumb_name  = excluded.thumb_name,
                    image_url   = excluded.image_url,
                    set_number  = excluded.set_number,
                    limit_break = excluded.limit_break,
                    lb_cost     = excluded.lb_cost
                """)) {
            ps.setString(1,  card.serial);
            ps.setString(2,  card.nameEn);
            ps.setString(3,  card.typeEn);
            ps.setString(4,  card.element);
            ps.setInt   (5,  card.cost);
            if (card.power != null) ps.setInt (6, card.power);
            else                    ps.setNull(6, Types.INTEGER);
            ps.setString(7,  card.rarity);
            ps.setString(8,  card.jobEn == null ? null : card.jobEn.replace("Warrrior", "Warrior").replace("―", ""));
            ps.setString(9,  card.category1);
            ps.setString(10, card.category2);
            ps.setInt   (11, card.exBurst  ? 1 : 0);
            ps.setInt   (12, card.multicard ? 1 : 0);
            String textEn = card.textEn == null ? null
                    : card.textEn.replaceAll("(?si)\\[\\[i\\]\\](.*?)\\[\\[/\\]\\]", "$1");
            ps.setString(13, textEn);
            ps.setString(14, card.thumbName);
            ps.setString(15, card.imageUrl);
            ps.setString(16, card.setNumber);
            ps.setInt   (17, computeLimitBreak(textEn));
            Integer lbCost = computeLbCost(textEn);
            if (lbCost != null) ps.setInt (18, lbCost);
            else                ps.setNull(18, Types.INTEGER);
            ps.executeUpdate();
        }
    }

    /** Saves a list of cards in a single transaction. */
    public void saveCards(List<ScrapedCard> cards) throws SQLException {
        conn.setAutoCommit(false);
        try {
            for (ScrapedCard card : cards) saveCard(card);
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    /** Stores raw image bytes for a card identified by its serial. */
    public void saveImageData(String serial, byte[] imageData) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE cards SET image_data = ? WHERE serial = ?")) {
            ps.setBytes(1, imageData);
            ps.setString(2, serial);
            ps.executeUpdate();
        }
    }

    /**
     * Stores raw image bytes for a card identified by its image URL.
     *
     * @return the number of rows updated (0 means no card with that image_url exists)
     */
    public int saveImageDataByUrl(String imageUrl, byte[] imageData) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE cards SET image_data = ? WHERE image_url = ?")) {
            ps.setBytes(1, imageData);
            ps.setString(2, imageUrl);
            return ps.executeUpdate();
        }
    }

    /**
     * Returns the cached image bytes for the given image URL,
     * or {@code null} if the blob has not yet been stored.
     */
    public byte[] getImageData(String imageUrl) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT image_data FROM cards WHERE image_url = ?")) {
            ps.setString(1, imageUrl);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBytes("image_data");
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public ScrapedCard getCard(String serial) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM cards WHERE serial = ?")) {
            ps.setString(1, serial);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapRow(rs) : null;
        }
    }

    public List<ScrapedCard> getCardsBySet(String setNumber) throws SQLException {
        return query("SELECT * FROM cards WHERE set_number = ? ORDER BY serial", setNumber);
    }

    public List<ScrapedCard> getCardsByType(String typeEn) throws SQLException {
        return query("SELECT * FROM cards WHERE type_en = ? ORDER BY serial", typeEn);
    }

    public List<ScrapedCard> getCardsByElement(String element) throws SQLException {
        return query("SELECT * FROM cards WHERE element = ? ORDER BY serial", element);
    }

    public List<ScrapedCard> searchByName(String namePart) throws SQLException {
        return query("SELECT * FROM cards WHERE name_en LIKE ? ORDER BY serial",
                "%" + namePart + "%");
    }

    /** Returns true if any cards are stored for the given set number. */
    public boolean hasSet(String setNumber) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM cards WHERE set_number = ? LIMIT 1")) {
            ps.setString(1, setNumber);
            return ps.executeQuery().next();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<ScrapedCard> query(String sql, String param) throws SQLException {
        List<ScrapedCard> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(mapRow(rs));
        }
        return result;
    }

    private ScrapedCard mapRow(ResultSet rs) throws SQLException {
        ScrapedCard c = new ScrapedCard();
        c.serial    = rs.getString("serial");
        c.nameEn    = rs.getString("name_en");
        c.typeEn    = rs.getString("type_en");
        c.element   = rs.getString("element");
        c.cost      = rs.getInt   ("cost");
        int p       = rs.getInt   ("power");
        c.power     = rs.wasNull() ? null : p;
        c.rarity    = rs.getString("rarity");
        c.jobEn     = rs.getString("job_en");
        c.category1 = rs.getString("category_1");
        c.category2 = rs.getString("category_2");
        c.exBurst   = rs.getInt("ex_burst")   == 1;
        c.multicard = rs.getInt("multicard")  == 1;
        c.textEn    = rs.getString("text_en");
        c.thumbName = rs.getString("thumb_name");
        c.imageUrl  = rs.getString("image_url");
        c.setNumber = rs.getString("set_number");
        return c;
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }

    /** Run via: mvn compile exec:java -Dexec.mainClass=scraper.CardDatabase */
    public static void main(String[] args) throws SQLException {
        try (CardDatabase db = new CardDatabase("fftcg_cards.db")) {
            System.out.println("Migration complete.");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static final String LB_PHRASE = "Limit Break -- ";

    private static int computeLimitBreak(String textEn) {
        return textEn != null && textEn.contains(LB_PHRASE) ? 1 : 0;
    }

    private static Integer computeLbCost(String textEn) {
        if (textEn == null) return null;
        int idx = textEn.indexOf(LB_PHRASE);
        if (idx < 0) return null;
        int start = idx + LB_PHRASE.length();
        int end = start;
        while (end < textEn.length() && Character.isDigit(textEn.charAt(end))) end++;
        return end > start ? Integer.parseInt(textEn.substring(start, end)) : null;
    }
}

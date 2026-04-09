package scraper;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SQLite persistence layer for FFTCG saved decks.
 *
 * Stores decks and their card lists in the same fftcg_cards.db used by CardDatabase.
 * Tables are created automatically on first use.
 *
 * Usage:
 *   try (DeckDatabase db = new DeckDatabase()) {
 *       int id = db.createDeck("My Deck");
 *       db.setCardCount(id, "1-001H", 3);
 *   }
 */
public class DeckDatabase implements AutoCloseable {

    private static final String DB_URL = "jdbc:sqlite:fftcg_cards.db";

    private final Connection conn;

    public DeckDatabase() throws SQLException {
        conn = DriverManager.getConnection(DB_URL);
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA foreign_keys=ON");
        }
        createSchema();
    }

    // -------------------------------------------------------------------------
    // Schema
    // -------------------------------------------------------------------------

    private void createSchema() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS decks (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    name       TEXT    NOT NULL,
                    created_at TEXT    DEFAULT (datetime('now')),
                    updated_at TEXT    DEFAULT (datetime('now'))
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS deck_cards (
                    deck_id INTEGER NOT NULL REFERENCES decks(id) ON DELETE CASCADE,
                    serial  TEXT    NOT NULL,
                    count   INTEGER NOT NULL DEFAULT 1,
                    PRIMARY KEY (deck_id, serial)
                )
                """);
        }
    }

    // -------------------------------------------------------------------------
    // Deck CRUD
    // -------------------------------------------------------------------------

    /** Creates a new empty deck and returns its generated ID. */
    public int createDeck(String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO decks (name) VALUES (?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    /** Renames an existing deck. */
    public void renameDeck(int id, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE decks SET name = ?, updated_at = datetime('now') WHERE id = ?")) {
            ps.setString(1, name);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    /** Deletes a deck and all its cards (cascades via foreign key). */
    public void deleteDeck(int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM decks WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /** Creates a copy of an existing deck under a new name and returns the new deck's ID. */
    public int copyDeck(int sourceId, String newName) throws SQLException {
        int newId = createDeck(newName);
        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO deck_cards (deck_id, serial, count) "
                + "SELECT ?, serial, count FROM deck_cards WHERE deck_id = ?")) {
            ps.setInt(1, newId);
            ps.setInt(2, sourceId);
            ps.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
        return newId;
    }

    /** Returns all decks ordered by creation (oldest first). */
    public List<DeckEntry> getDecks() throws SQLException {
        List<DeckEntry> result = new ArrayList<>();
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, name FROM decks ORDER BY id")) {
            while (rs.next()) result.add(new DeckEntry(rs.getInt("id"), rs.getString("name")));
        }
        return result;
    }

    /** Returns all decks with their total card counts, ordered by creation (oldest first). */
    public List<DeckSummary> getDecksSummary() throws SQLException {
        List<DeckSummary> result = new ArrayList<>();
        String sql = """
                SELECT d.id, d.name, COALESCE(SUM(dc.count), 0) AS total
                FROM decks d
                LEFT JOIN deck_cards dc ON d.id = dc.deck_id
                GROUP BY d.id, d.name
                ORDER BY d.id
                """;
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                result.add(new DeckSummary(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("total")));
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Deck card operations
    // -------------------------------------------------------------------------

    /**
     * Returns all cards in the deck joined with card metadata.
     * Each row: [serial, name_en, type_en, element, cost, power, rarity, count]
     */
    public List<Object[]> getDeckCards(int deckId) throws SQLException {
        List<Object[]> result = new ArrayList<>();
        String sql = """
            SELECT dc.serial, c.name_en, c.type_en, c.element, c.cost, c.power, c.rarity, dc.count
            FROM deck_cards dc
            LEFT JOIN cards c ON dc.serial = c.serial
            WHERE dc.deck_id = ?
            ORDER BY dc.serial
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, deckId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Object[]{
                        rs.getString("serial"),
                        rs.getString("name_en"),
                        rs.getString("type_en"),
                        rs.getString("element"),
                        rs.getObject("cost"),
                        rs.getObject("power"),
                        rs.getString("rarity"),
                        rs.getInt("count")
                    });
                }
            }
        }
        return result;
    }

    /** Returns the total number of cards (sum of counts) in the deck. */
    public int getTotalCardCount(int deckId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(count), 0) FROM deck_cards WHERE deck_id = ?")) {
            ps.setInt(1, deckId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Returns how many copies of a specific card are in the deck (0 if none). */
    public int getCardCount(int deckId, String serial) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT count FROM deck_cards WHERE deck_id = ? AND serial = ?")) {
            ps.setInt(1, deckId);
            ps.setString(2, serial);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("count") : 0;
            }
        }
    }

    /**
     * Sets the copy count for a card in a deck.
     * If count <= 0, the card entry is removed entirely.
     */
    public void setCardCount(int deckId, String serial, int count) throws SQLException {
        if (count <= 0) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM deck_cards WHERE deck_id = ? AND serial = ?")) {
                ps.setInt(1, deckId);
                ps.setString(2, serial);
                ps.executeUpdate();
            }
        } else {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO deck_cards (deck_id, serial, count) VALUES (?, ?, ?)
                    ON CONFLICT(deck_id, serial) DO UPDATE SET count = excluded.count
                    """)) {
                ps.setInt(1, deckId);
                ps.setString(2, serial);
                ps.setInt(3, count);
                ps.executeUpdate();
            }
        }
        touchDeck(deckId);
    }

    // -------------------------------------------------------------------------
    // Card browser data (reuses the same connection to avoid extra open/close)
    // -------------------------------------------------------------------------

    /** Returns the set of serials whose limit_break flag is 1. */
    public Set<String> getLbSerials() throws SQLException {
        Set<String> result = new HashSet<>();
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT serial FROM cards WHERE limit_break = 1")) {
            while (rs.next()) result.add(rs.getString("serial"));
        }
        return result;
    }

    /**
     * Returns all cards from the cards table for the inline browser.
     * Each row: [serial, name_en, type_en, element, cost, power, rarity, job_en, category_1, category_2]
     */
    public List<Object[]> getAllCards() throws SQLException {
        List<Object[]> result = new ArrayList<>();
        String sql = "SELECT serial, name_en, type_en, element, cost, power, rarity, "
                   + "job_en, category_1, category_2 FROM cards "
                   + "WHERE type_en != 'Crystal' ORDER BY serial";
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                result.add(new Object[]{
                    rs.getString("serial"),
                    rs.getString("name_en"),
                    rs.getString("type_en"),
                    rs.getString("element"),
                    rs.getObject("cost"),
                    rs.getObject("power"),
                    rs.getString("rarity"),
                    rs.getString("job_en"),
                    rs.getString("category_1"),
                    rs.getString("category_2")
                });
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void touchDeck(int deckId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE decks SET updated_at = datetime('now') WHERE id = ?")) {
            ps.setInt(1, deckId);
            ps.executeUpdate();
        }
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }

    // -------------------------------------------------------------------------
    // DeckEntry record
    // -------------------------------------------------------------------------

    /** Lightweight representation of a saved deck for display in the UI. */
    public record DeckEntry(int id, String name) {
        @Override
        public String toString() { return name; }
    }

    /** Deck with its total card count, used for deck selection. */
    public record DeckSummary(int id, String name, int cardCount) {
        @Override
        public String toString() { return name; }
    }
}

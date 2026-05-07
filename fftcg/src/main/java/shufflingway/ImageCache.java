package shufflingway;

import scraper.CardDatabase;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-level image cache that layers three sources:
 * <ol>
 *   <li><b>Memory</b> — a {@link ConcurrentHashMap} keyed by image URL, populated
 *       on first access and retained for the lifetime of the JVM.</li>
 *   <li><b>Database</b> — the {@code image_data} BLOB column in
 *       {@code fftcg_cards.db}.  Populated from the network on first download.</li>
 *   <li><b>Network</b> — falls back to an HTTP download when no blob is stored.
 *       The downloaded bytes are automatically persisted to the DB so subsequent
 *       sessions skip the network entirely.</li>
 * </ol>
 *
 * <p>All DB access is serialised through a single long-lived {@link CardDatabase}
 * connection.  Call {@link #shutdown()} once on application exit to close it.</p>
 */
public final class ImageCache {

    private static final ConcurrentHashMap<String, byte[]> mem = new ConcurrentHashMap<>();
    private static CardDatabase db;

    private ImageCache() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns a decoded {@link Image} for the given URL.
     * Sources are tried in order: memory → DB → network.
     * A successful network fetch is persisted to the DB automatically.
     *
     * @param url image URL; {@code null} returns {@code null}
     * @throws IOException if a network fetch was required and failed
     */
    public static Image load(String url) throws IOException {
        if (url == null) return null;

        byte[] bytes = mem.get(url);

        if (bytes == null) {
            bytes = fetchFromDb(url);
            if (bytes != null) mem.putIfAbsent(url, bytes);
        }

        if (bytes == null) {
            bytes = fetchFromNetwork(url);
            if (bytes != null) {
                mem.putIfAbsent(url, bytes);
                persistToDb(url, bytes);
            }
        }

        if (bytes == null) return null;
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    /** Closes the backing DB connection.  Safe to call even if never opened. */
    public static synchronized void shutdown() {
        if (db != null) {
            try { db.close(); } catch (SQLException ignored) {}
            db = null;
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static synchronized byte[] fetchFromDb(String url) {
        try {
            return openDb().getImageData(url);
        } catch (Exception e) {
            return null;
        }
    }

    private static synchronized void persistToDb(String url, byte[] bytes) {
        try {
            int rows = openDb().saveImageDataByUrl(url, bytes);
            if (rows == 0) {
                System.err.println("[ImageCache] persistToDb: no card row has image_url = " + url);
            }
        } catch (Exception e) {
            System.err.println("[ImageCache] persistToDb failed for " + url + ": " + e.getMessage());
        }
    }

    /** Opens the DB connection on first use; reuses it thereafter. */
    private static CardDatabase openDb() throws SQLException {
        if (db == null) db = new CardDatabase("fftcg_cards.db");
        return db;
    }

    private static byte[] fetchFromNetwork(String url) throws IOException {
        try (var in  = new URL(url).openStream();
             var out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }
}

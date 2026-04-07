package scraper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Fetches card metadata from the official Square Enix FFTCG card browser API.
 *
 * Usage:
 *   CardScraper scraper = new CardScraper();
 *   List<ScrapedCard> opus1 = scraper.scrapeSet("1");   // Opus I
 *   List<ScrapedCard> all   = scraper.scrapeAllSets(1, 22);
 *
 * NOTE: If any field names look wrong, open the card browser in your browser's
 * DevTools → Network tab, trigger a search, and inspect the POST payload/response
 * to confirm the exact field names used by the current API version.
 */
public class CardScraper {

    private static final String API_URL =
            "https://fftcg.square-enix-games.com/na/card-browser/getCards";
    private static final String IMAGE_BASE_URL =
            "https://fftcg.cdn.sewest.net/images/cards/full/";

    /** Cards returned per API page. The SE API accepts up to ~60. */
    private static final int PAGE_SIZE = 60;

    /** Polite delay between pages so we don't hammer the server (ms). */
    private static final long DELAY_MS = 500;

    private final HttpClient http;

    public CardScraper() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Scrapes all cards for a single set / Opus number.
     *
     * @param setNumber Opus number as a string, e.g. "1" for Opus I, "22" for Opus XXII.
     *                  Use "PR" or "PROMO" for promotional cards (verify exact value).
     */
    public List<ScrapedCard> scrapeSet(String setNumber) throws IOException, InterruptedException {
        List<ScrapedCard> result = new ArrayList<>();
        int searchStart = 0;
        int totalCount  = Integer.MAX_VALUE;

        System.out.printf("Scraping set %s…%n", setNumber);

        while (searchStart < totalCount) {
            String body = buildRequestBody(setNumber, searchStart, PAGE_SIZE);
            JSONObject json = post(body);

            totalCount = json.optInt("count", 0);
            JSONArray cards = json.optJSONArray("cards");

            if (cards == null || cards.isEmpty()) break;

            for (int i = 0; i < cards.length(); i++) {
                result.add(parseCard(cards.getJSONObject(i), setNumber));
            }

            System.out.printf("  … %d / %d%n", result.size(), totalCount);
            searchStart += PAGE_SIZE;

            if (searchStart < totalCount) {
                Thread.sleep(DELAY_MS);
            }
        }

        return result;
    }

    /**
     * Scrapes a consecutive range of Opus sets (inclusive on both ends).
     *
     * @param firstOpus e.g. 1
     * @param lastOpus  e.g. 22
     */
    public List<ScrapedCard> scrapeAllSets(int firstOpus, int lastOpus)
            throws IOException, InterruptedException {
        List<ScrapedCard> all = new ArrayList<>();
        for (int i = firstOpus; i <= lastOpus; i++) {
            all.addAll(scrapeSet(String.valueOf(i)));
        }
        return all;
    }

    /**
     * Downloads the raw bytes of a card image from the CDN.
     * Store the bytes in SQLite (BLOB) or write them to disk as you prefer.
     */
    public byte[] downloadImage(String imageUrl) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());

        if (resp.statusCode() != 200) {
            throw new IOException("Image download failed (HTTP " + resp.statusCode() + "): " + imageUrl);
        }
        return resp.body();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private JSONObject post(String body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                // Identify ourselves honestly
                .header("User-Agent", "FFTCG-Card-Scraper/1.0 (personal project)")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200) {
            throw new IOException("API error (HTTP " + resp.statusCode() + "): " + resp.body());
        }
        return new JSONObject(resp.body());
    }

    private String buildRequestBody(String setNumber, int searchStart, int count) {
        // Outer sort array: [{"sort": "Serial", "order": "asc"}]
        JSONArray sort = new JSONArray()
                .put(new JSONObject().put("sort", "Serial").put("order", "asc"));

        return new JSONObject()
                .put("language",     "en")
                .put("setNum",       setNumber)
                .put("type",         new JSONArray())
                .put("element",      new JSONArray())
                .put("cost",         new JSONArray())
                .put("rarity",       new JSONArray())
                .put("power",        new JSONArray())
                .put("category",     new JSONArray())
                .put("multicard",    false)
                .put("ex_burst",     false)
                .put("search_start", searchStart)
                .put("count",        count)
                .put("sort",         sort)
                .toString();
    }

    private ScrapedCard parseCard(JSONObject j, String setNumber) {
        ScrapedCard c = new ScrapedCard();

        c.serial    = j.optString("Serial",    "");
        c.nameEn    = j.optString("Name_EN",   "");
        c.typeEn    = j.optString("Type_EN",   "");
        c.element   = j.optString("Element",   "");
        c.rarity    = j.optString("Rarity",    "");
        c.jobEn     = j.optString("Job_EN",    "");
        c.category1 = j.optString("Category_1","");
        c.category2 = j.optString("Category_2","");
        c.exBurst   = j.optBoolean("Ex_Burst",  false);
        c.multicard = j.optBoolean("Multicard", false);
        c.textEn    = j.optString("Text_EN",   "");
        c.thumbName = j.optString("ThumbName", "");
        c.setNumber = setNumber;

        // Cost can arrive as int or string from the API
        Object rawCost = j.opt("Cost");
        if (rawCost instanceof Number n) {
            c.cost = n.intValue();
        } else if (rawCost instanceof String s && !s.isBlank()) {
            try { c.cost = Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
        }

        // Power is only present on Forwards and Monsters; may be "" or absent for others
        Object rawPower = j.opt("Power");
        if (rawPower instanceof Number n) {
            c.power = n.intValue();
        } else if (rawPower instanceof String s && !s.isBlank()) {
            try { c.power = Integer.parseInt(s.replace(",", "").trim()); }
            catch (NumberFormatException ignored) {}
        }

        if (!c.thumbName.isEmpty()) {
            c.imageUrl = IMAGE_BASE_URL + c.thumbName;
        }

        return c;
    }
}

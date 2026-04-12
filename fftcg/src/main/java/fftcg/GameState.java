package fftcg;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Holds all mutable game state for an in-progress FFTCG match.
 * Pure data/logic — no Swing dependencies.
 */
public class GameState {

    // --- P1 ---
    private final Deque<String> p1MainDeck = new ArrayDeque<>();
    private final List<String>  p1LbDeck   = new ArrayList<>();
    private final List<String>  p1Hand     = new ArrayList<>();
    private boolean p1OpeningHandPending = false;
    private boolean p1MulliganUsed       = false;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Resets all state for a new game. */
    public void reset() {
        p1MainDeck.clear();
        p1LbDeck.clear();
        p1Hand.clear();
        p1OpeningHandPending = false;
        p1MulliganUsed       = false;
    }

    /**
     * Populates decks from lists of image URLs (already separated into main / LB).
     * Shuffles the main deck before loading it.
     */
    public void initializeDeck(List<String> mainUrls, List<String> lbUrls) {
        List<String> shuffled = new ArrayList<>(mainUrls);
        Collections.shuffle(shuffled);
        p1MainDeck.addAll(shuffled);
        p1LbDeck.addAll(lbUrls);
        p1OpeningHandPending = true;
    }

    // -------------------------------------------------------------------------
    // Opening hand
    // -------------------------------------------------------------------------

    /**
     * Draws up to 5 cards from the top of the main deck.
     *
     * @return the drawn cards in draw order (index 0 = first card drawn)
     */
    public List<String> drawOpeningHand() {
        List<String> drawn = new ArrayList<>();
        for (int i = 0; i < 5 && !p1MainDeck.isEmpty(); i++) {
            drawn.add(p1MainDeck.poll());
        }
        return drawn;
    }

    /**
     * Mulligan: places {@code bottomOrder} on the bottom of the main deck in
     * that order, marks the mulligan as used, then draws a fresh 5 cards.
     *
     * @param bottomOrder the opening-hand cards in the order the player wants
     *                    them placed on the bottom (index 0 goes first / deepest)
     * @return the newly drawn 5 cards
     */
    public List<String> mulligan(List<String> bottomOrder) {
        for (String url : bottomOrder) {
            p1MainDeck.addLast(url);
        }
        p1MulliganUsed = true;
        return drawOpeningHand();
    }

    /**
     * Finalises the opening hand: moves the given cards into p1Hand and
     * clears the opening-hand-pending flag.
     *
     * @param cards the cards in the order they were kept
     */
    public void keepHand(List<String> cards) {
        p1Hand.addAll(cards);
        p1OpeningHandPending = false;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public Deque<String> getP1MainDeck()           { return p1MainDeck; }
    public List<String>  getP1LbDeck()             { return p1LbDeck; }
    public List<String>  getP1Hand()               { return p1Hand; }
    public boolean       isP1OpeningHandPending()  { return p1OpeningHandPending; }
    public boolean       isP1MulliganUsed()        { return p1MulliganUsed; }
}

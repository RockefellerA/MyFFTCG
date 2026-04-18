package fftcg;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds all mutable game state for an in-progress FFTCG match.
 * Pure data/logic — no Swing dependencies.
 */
public class GameState {

    // --- P1 ---
    private final Deque<CardData>          p1MainDeck   = new ArrayDeque<>();
    private final List<CardData>           p1LbDeck     = new ArrayList<>();
    private final List<CardData>           p1Hand       = new ArrayList<>();
    private final List<CardData>           p1BreakZone  = new ArrayList<>();
    private final List<CardData>           p1DamageZone = new ArrayList<>();
    private final Map<String, Integer>     p1CpByElement = new HashMap<>();
    private boolean p1OpeningHandPending  = false;
    private boolean p1MulliganUsed        = false;
    private boolean p1GameOver            = false;

    // --- P2 ---
    private final Deque<CardData>          p2MainDeck   = new ArrayDeque<>();
    private final List<CardData>           p2DamageZone = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Resets all state for a new game. */
    public void reset() {
        p1MainDeck.clear();
        p1LbDeck.clear();
        p1Hand.clear();
        p1BreakZone.clear();
        p1DamageZone.clear();
        p1CpByElement.clear();
        p1OpeningHandPending = false;
        p1MulliganUsed       = false;
        p1GameOver           = false;
        p2MainDeck.clear();
        p2DamageZone.clear();
        currentPhase         = null;
        turnNumber           = 0;
    }

    /**
     * Populates decks from lists of CardData (already separated into main / LB).
     * Shuffles the main deck before loading it.
     */
    public void initializeDeck(List<CardData> mainCards, List<CardData> lbCards) {
        List<CardData> shuffled = new ArrayList<>(mainCards);
        Collections.shuffle(shuffled);
        p1MainDeck.addAll(shuffled);
        p1LbDeck.addAll(lbCards);
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
    public List<CardData> drawOpeningHand() {
        List<CardData> drawn = new ArrayList<>();
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
    public List<CardData> mulligan(List<CardData> bottomOrder) {
        for (CardData card : bottomOrder) {
            p1MainDeck.addLast(card);
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
    public void keepHand(List<CardData> cards) {
        p1Hand.addAll(cards);
        p1OpeningHandPending = false;
    }

    // -------------------------------------------------------------------------
    // Draw
    // -------------------------------------------------------------------------

    /**
     * Draws {@code count} cards from the top of the main deck into the hand.
     *
     * @return the cards actually drawn (may be fewer than {@code count} if the deck runs out)
     */
    public List<CardData> drawToHand(int count) {
        List<CardData> drawn = new ArrayList<>();
        for (int i = 0; i < count && !p1MainDeck.isEmpty(); i++) {
            CardData card = p1MainDeck.poll();
            p1Hand.add(card);
            drawn.add(card);
        }
        return drawn;
    }

    // -------------------------------------------------------------------------
    // Damage zone
    // -------------------------------------------------------------------------

    /**
     * Draws the top card of the main deck into the damage zone.
     *
     * @return the card placed in the damage zone, or {@code null} if the deck is empty
     */
    public CardData drawToDamageZone() {
        if (p1MainDeck.isEmpty()) return null;
        CardData card = p1MainDeck.poll();
        p1DamageZone.add(card);
        return card;
    }

    /** Draws the top card of P2's deck into P2's damage zone. */
    public CardData drawToP2DamageZone() {
        if (p2MainDeck.isEmpty()) return null;
        CardData card = p2MainDeck.poll();
        p2DamageZone.add(card);
        return card;
    }

    /** Shuffles {@code mainCards} and loads them as P2's main deck. */
    public void initializeP2Deck(List<CardData> mainCards) {
        List<CardData> shuffled = new ArrayList<>(mainCards);
        Collections.shuffle(shuffled);
        p2MainDeck.addAll(shuffled);
    }

    // -------------------------------------------------------------------------
    // Hand actions
    // -------------------------------------------------------------------------

    /**
     * Discards a card from the player's hand to the Break Zone and awards 2 CP
     * of the card's element.  Light and Dark cards cannot be discarded for CP
     * (they are still moved to the Break Zone but grant 0 CP).
     *
     * @param idx index into p1Hand
     * @return the discarded CardData, or {@code null} if idx is invalid
     */
    public CardData discardFromHand(int idx) {
        if (idx < 0 || idx >= p1Hand.size()) return null;
        CardData card = p1Hand.remove(idx);
        p1BreakZone.add(card);
        if (!card.isLightOrDark()) {
            addP1Cp(card.element(), 2);
        }
        return card;
    }

    /**
     * Removes a card from the player's hand without sending it to the Break Zone
     * and without granting CP.  Used when playing a card (the card goes to a zone
     * via separate logic).
     *
     * @param idx index into p1Hand
     * @return the removed CardData, or {@code null} if idx is invalid
     */
    public CardData removeFromHand(int idx) {
        if (idx < 0 || idx >= p1Hand.size()) return null;
        return p1Hand.remove(idx);
    }

    /**
     * Sends a card from the player's hand to the Break Zone without granting CP.
     * Used for payment discards (CP is credited separately via addP1Cp with the
     * correct contributing element) and for mandatory end-phase discards.
     *
     * @param idx index into p1Hand
     * @return the discarded CardData, or {@code null} if idx is invalid
     */
    public CardData breakFromHand(int idx) {
        if (idx < 0 || idx >= p1Hand.size()) return null;
        CardData card = p1Hand.remove(idx);
        p1BreakZone.add(card);
        return card;
    }

    // -------------------------------------------------------------------------
    // Crystal Points
    // -------------------------------------------------------------------------

    /**
     * Returns the current CP for the given element (0 if never accumulated).
     */
    public int getP1CpForElement(String element) {
        return p1CpByElement.getOrDefault(element, 0);
    }

    /** Returns an unmodifiable view of all CP buckets. */
    public Map<String, Integer> getP1CpByElement() {
        return Collections.unmodifiableMap(p1CpByElement);
    }

    /** Adds {@code amount} CP to the given element bucket. */
    public void addP1Cp(String element, int amount) {
        p1CpByElement.merge(element, amount, Integer::sum);
    }

    /**
     * Spends {@code amount} CP from the given element bucket if available.
     *
     * @return {@code true} if the spend succeeded, {@code false} if insufficient CP
     */
    public boolean spendP1Cp(String element, int amount) {
        int current = getP1CpForElement(element);
        if (current < amount) return false;
        p1CpByElement.put(element, current - amount);
        return true;
    }

    /** Zeroes the CP bucket for the given element. */
    public void clearP1Cp(String element) {
        p1CpByElement.put(element, 0);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public Deque<CardData> getP1MainDeck()          { return p1MainDeck; }
    public List<CardData>  getP1LbDeck()            { return p1LbDeck; }
    public List<CardData>  getP1Hand()              { return p1Hand; }
    public List<CardData>  getP1BreakZone()         { return p1BreakZone; }
    public List<CardData>  getP1DamageZone()        { return p1DamageZone; }
    public boolean         isP1OpeningHandPending() { return p1OpeningHandPending; }
    public boolean         isP1MulliganUsed()       { return p1MulliganUsed; }
    public boolean         isP1GameOver()           { return p1GameOver; }
    public void            setP1GameOver(boolean v) { p1GameOver = v; }
    public Deque<CardData> getP2MainDeck()          { return p2MainDeck; }
    public List<CardData>  getP2DamageZone()        { return p2DamageZone; }

    // -------------------------------------------------------------------------
    // Phase / turn tracking
    // -------------------------------------------------------------------------

    /**
     * The six phases of a FFTCG turn, in order.
     * Calling {@link #next()} wraps END back to ACTIVE.
     */
    public enum GamePhase {
        ACTIVE ("Active Phase"),
        DRAW   ("Draw Phase"),
        MAIN_1 ("Main Phase 1"),
        ATTACK ("Attack Phase"),
        MAIN_2 ("Main Phase 2"),
        END    ("End Phase");

        public final String displayName;
        GamePhase(String displayName) { this.displayName = displayName; }

        /** Returns the phase that follows this one in turn order. */
        public GamePhase next() {
            GamePhase[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    private GamePhase currentPhase = null;  // null ⇒ game not yet started
    private int       turnNumber   = 0;

    /** Begins the very first turn: sets phase to Active and turn number to 1. */
    public void startFirstTurn() {
        currentPhase = GamePhase.ACTIVE;
        turnNumber   = 1;
    }

    /**
     * Advances to the next phase in turn order.
     * When {@link GamePhase#END} wraps back to {@link GamePhase#ACTIVE}
     * the turn number is incremented.
     *
     * @return the newly entered phase
     */
    public GamePhase advancePhase() {
        if (currentPhase == null) {
            currentPhase = GamePhase.ACTIVE;
            turnNumber   = 1;
        } else {
            if (currentPhase == GamePhase.END) turnNumber++;
            currentPhase = currentPhase.next();
        }
        return currentPhase;
    }

    public GamePhase getCurrentPhase() { return currentPhase; }
    public int       getTurnNumber()   { return turnNumber; }
}

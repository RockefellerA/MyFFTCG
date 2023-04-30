package zone;

import java.util.Collections;

import model.card.Card;

public class Deck extends Zone{
	
	// Each deck may have only 3 copies of each card.
	// If a player takes damage while their deck has 0 cards left, they lose the game.
	
	public int MAXIMUM_DECK_SIZE = 50; // Each deck must start with exactly 50 cards
	
	public boolean showTopCard; // If the player has an active effect that displays the top card.
	
	public Deck() {
		super();
	}

	/**
	 * Shuffle the order of the cards in the deck object.
	 */
	public void shuffle() {
		Collections.shuffle(cards);
	}
	
	public void drawCard() {
		Card addToHand;
		try {
			addToHand = cards.get(1);
			// TODO: Player.hand.cards.add(addToHand);
			cards.remove(1);
		} catch (IndexOutOfBoundsException ie) {
			// TODO: Player loses the game.
		}
	}
	
	public int getDeckSize() {
		return cards.size();
	}
}

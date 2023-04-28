package model;

import java.util.ArrayList;
import java.util.Collections;

import model.card.Card;

public class Deck {
	
	// Each deck may have only 3 copies of each card.
	// If a player takes damage while their deck has 0 cards left, they lose the game.
	
	public int MAXIMUM_DECK_SIZE = 50; // Each deck must consist of exactly 50 cards
	
	public ArrayList<Card> cards;
	
	/**
	 * Shuffle the order of the cards in the deck object.
	 */
	public void Shuffle() {
		Collections.shuffle(cards);
	}

}

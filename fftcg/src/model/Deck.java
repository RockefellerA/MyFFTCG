package model;

import java.util.ArrayList;
import java.util.Collections;

import model.card.Card;

public class Deck {
	
	public int MAXIMUM_DECK_SIZE = 50; // Each deck must consist of exactly 50 cards
	
	public ArrayList<Card> cards;
	
	/**
	 * Shuffle the order of the cards in the deck object.
	 */
	public void Shuffle() {
		Collections.shuffle(cards);
	}

}

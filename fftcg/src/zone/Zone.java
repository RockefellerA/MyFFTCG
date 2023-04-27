package zone;

import java.util.ArrayList;

import model.card.Card;

public abstract class Zone {
	
	public ArrayList<Card> cards;

	public ArrayList<Card> getCards() {
		return cards;
	}

	public void setCards(ArrayList<Card> cards) {
		this.cards = cards;
	}

}

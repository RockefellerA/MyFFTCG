package zone;

import model.card.Card;

public class LimitBreakDeck extends Zone{
	
	// Limit Break Zone is a new mechanic as of 2024 that is essentially an optional side deck.
	// You can have up to 8 LB cards in the zone.  When you cast one, you must also flip/sacrifice
	// a certain number of LB cards.
	// LB cards can be cast any time you can normally cast a card.
	
	public int MAX_LB_ZONE_SIZE = 8;
	
	public LimitBreakDeck() {
		super();
	}
	
	public void flipCard (Card card) {
		// flip/sacrifice the selected card, making it unable to be played for the rest of the game.
	}

}

package phase;

import zone.Hand;

public class EndPhase {
	
	// End all effects marked as 'until the end of the turn'.
	// All damage dealt to Forwards present on field is cleared.
	// If you have more than 5 cards in hand, discard down to 5.
	
	void discardTo5 (Hand hand) {
		if (hand.getCards().size() > 5) {
			// Force player to choose cards to discard.
		}
	}

}

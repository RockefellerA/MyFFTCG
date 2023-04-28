package zone;

public class DamageZone extends Zone{
	
	// When a player takes damage, they flip a card from the top of their deck.
	// That card enters the damage zone, reducing their HP by 1.
	// If the player has no cards remaining in their deck to add to the Damage Zone, they lose the game.
	
	// If the card has an EX Burst ability, that ability is triggered.
	// EX Bursts are optional, just in case they have a downside.
	
	// If a player takes more than one damage from one source, take all points of damage into their Damage Zone,
	// and then resolve any triggered EX Bursts in the order they entered the Damage Zone.

	
	public int damage; // Starts at 0
	
}

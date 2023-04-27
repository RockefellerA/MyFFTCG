package zone;

public class DamageZone extends Zone{
	
	// When a player takes damage, they flip a card from the top of their deck.
	// That card enters the damage zone, reducing their HP by 1.
	// If the card has an EX Burst ability, that ability is triggered.
	
	public int damage; // Starts at 0
	
}

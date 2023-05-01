package model.card;

public class CardForward extends Card{
	
	// Backups are played active
	
	/**
	 * Things only Forwards have
	 */
	public int power;
	public boolean isPartied;
	public boolean isAttacking;
	public boolean isBlocking;
	
	public int damageTaken;
	
	public void takeDamage(int damage) {
		power = power - damage;
		if (power <= 0) {
			breakForward();
		}
	}
	
	private void breakForward() {
		// Move card to break zone.
	}

}

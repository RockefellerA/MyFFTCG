package model.card;

public class CardMonster extends Card{
	
	// Monsters do not get summoning sickness unless they become a forward.
	
	public int power; // Monsters can become Forwards
	public boolean isPartied;
	public boolean isAttacking;
	public boolean isBlocking;

}

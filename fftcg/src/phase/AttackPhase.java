package phase;

import java.util.Set;

import model.card.Card;
import model.card.CardForward;

public class AttackPhase extends Phase{
	
	// Each forward can only attack once unless otherwise stated (like Ravana)
	// They need to be un-dulled by some effect to attack again.
	
	public Card attacker;
	public Card blocker;
	
	public Set<CardForward> party;

}

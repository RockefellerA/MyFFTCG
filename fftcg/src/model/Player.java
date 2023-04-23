package model;

import java.util.Set;

public class Player {

	public int hp; // Standard HP is 7
	
	public Deck deck;
	
	public Hand hand;
	
	public int crystalPoints; // think mana but it expires immediately after the cast
	
	public Set<Elements> crystalPointElements; // for multi-element casts
	
	public int crystals; // yes, they really made two currencies named crystal, this one persists
}

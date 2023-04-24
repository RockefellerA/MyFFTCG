package model.card;

import java.util.Set;

import model.Category;
import model.Elements;
import model.Keywords;

public abstract class Card {
	
	/**
	 * These are things all cards have.
	 */
	public String name;
	public int cost;
	public Category category;
	public String cardNumber;
	public String rarity;
	public boolean hasExBurst;
	public Set<Elements> elements;
	public Set<Keywords> keywords;
	
	public boolean isCharacter;
	
	/**
	 * States of being
	 */
	public boolean isDull;
	public boolean isFrozen;
	public boolean isBroken;
	public boolean isRemoved;
	
	/**
	 * Misc data
	 */
	public boolean isFoil;
	public boolean isFullArt;
}

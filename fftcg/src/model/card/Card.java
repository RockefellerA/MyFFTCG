package model.card;

import java.util.Set;

import model.Category;
import model.Elements;
import model.Keywords;
import model.Rarity;

public abstract class Card {
	
	/**
	 * These are things all cards have.
	 */
	public String name;
	public int cost;
	public String job;
	public Set<Category> categories;
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
	public String cardNumber;
	public Rarity rarity;
	public boolean isFoil;
	public boolean isFullArt;
	
	
	// Full Card Size: 429x600px from fftcg browser
	
}

package model.card;

import java.awt.Image;
import java.util.Set;

import model.Category;
import model.Elements;
import model.Keywords;
import model.Player;
import model.Rarity;

public abstract class Card {
	
	// Full Card Size: 429x600px from fftcg browser
	
	/**
	 * These are things all cards have.
	 */
	public String name;
	public int cost;  // If cost=1, you still need one CP of each element. If cost=0, no CP is needed.\
	public int lbCost = 0; // only applies to LB cards
	public String job;
	public Set<Category> categories;
	public boolean hasExBurst;
	public Set<Elements> elements;
	public Set<Keywords> keywords;
	
	public boolean isCharacter;
	public boolean isLimitBreak; // LB cards can only be found in the LB deck.
	
	/**
	 * States of being
	 */
	public Player owner;
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
	public Image cardBack;
	public Image cardFront;
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	public int getCost() {
		return cost;
	}
	public void setCost(int cost) {
		this.cost = cost;
	}
	public String getJob() {
		return job;
	}
	public void setJob(String job) {
		this.job = job;
	}
	public Set<Category> getCategories() {
		return categories;
	}
	public void setCategories(Set<Category> categories) {
		this.categories = categories;
	}
	public boolean isHasExBurst() {
		return hasExBurst;
	}
	public void setHasExBurst(boolean hasExBurst) {
		this.hasExBurst = hasExBurst;
	}
	public Set<Elements> getElements() {
		return elements;
	}
	public void setElements(Set<Elements> elements) {
		this.elements = elements;
	}
	public Set<Keywords> getKeywords() {
		return keywords;
	}
	public void setKeywords(Set<Keywords> keywords) {
		this.keywords = keywords;
	}
	public boolean isCharacter() {
		return isCharacter;
	}
	public void setCharacter(boolean isCharacter) {
		this.isCharacter = isCharacter;
	}
	public boolean isLimitBreak() {
		return isLimitBreak;
	}
	public void setLimitBreak(boolean isLimitBreak) {
		this.isLimitBreak = isLimitBreak;
	}
	public Player getOwner() {
		return owner;
	}
	public void setOwner(Player owner) {
		this.owner = owner;
	}
	public boolean isDull() {
		return isDull;
	}
	public void setDull(boolean isDull) {
		this.isDull = isDull;
	}
	public boolean isFrozen() {
		return isFrozen;
	}
	public void setFrozen(boolean isFrozen) {
		this.isFrozen = isFrozen;
	}
	public boolean isBroken() {
		return isBroken;
	}
	public void setBroken(boolean isBroken) {
		this.isBroken = isBroken;
	}
	public boolean isRemoved() {
		return isRemoved;
	}
	public void setRemoved(boolean isRemoved) {
		this.isRemoved = isRemoved;
	}
	public String getCardNumber() {
		return cardNumber;
	}
	public void setCardNumber(String cardNumber) {
		this.cardNumber = cardNumber;
	}
	public Rarity getRarity() {
		return rarity;
	}
	public void setRarity(Rarity rarity) {
		this.rarity = rarity;
	}
	public boolean isFoil() {
		return isFoil;
	}
	public void setFoil(boolean isFoil) {
		this.isFoil = isFoil;
	}
	public boolean isFullArt() {
		return isFullArt;
	}
	public void setFullArt(boolean isFullArt) {
		this.isFullArt = isFullArt;
	}
	
	public Image getCardBack() {
		return cardBack;
	}

	public void setCardBack(Image cardBack) {
		this.cardBack = cardBack;
	}

	public Image getCardFront() {
		return cardFront;
	}

	public void setCardFront(Image cardFront) {
		this.cardFront = cardFront;
	}
	
}

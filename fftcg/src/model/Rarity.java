package model;

public enum Rarity {
	COMMON ("C"), 
	RARE ("R"), 
	HERO ("H"), 
	LEGEND ("L"), 
	STARTER ("S"), 
	PROMO ("P");
	
	private final String letter;
	
	Rarity(String letter) {
		this.letter = letter;
	}

	public String getLetter() {
		return letter;
	}
	
}

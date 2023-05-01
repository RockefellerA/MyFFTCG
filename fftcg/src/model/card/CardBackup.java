package model.card;

import model.Elements;

public class CardBackup extends Card{
	
	// Backups are played dull
	
	/**
	 * Things only Backups have
	 */
	public int backupSlot; // slot 1-5, 5 maximum per player.
	
	public void generateCP(Elements element) {
		this.isDull = true;
		this.owner.crystalPoints += 1;
		if (!this.owner.crystalPointElements.contains(element)) {
			this.owner.crystalPointElements.add(element); // add element to list if it isn't already there
		}
		// 99% of the time, backups can only generate the one color they are.
		// There are some cards / abilities that let backups generate/be any element, so you choose.
		// In the future there could be multicolor backups.
	}

}

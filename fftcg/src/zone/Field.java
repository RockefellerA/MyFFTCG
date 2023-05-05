package zone;

public class Field extends Zone{
	
	// Forwards, Backups, and Monsters go here.
	// Each player can only have one light or dark character on their side of the Field.
	
	public int numberOfBackups; // Only 5 allowed.
	
	public boolean hasLightOrDarkCharacter;

	public int getNumberOfBackups() {
		return numberOfBackups;
	}

}

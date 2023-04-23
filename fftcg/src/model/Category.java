package model;

public enum Category {
	I ("I", "Final Fantasy"),
	II("II", "Final Fantasy II"),
	III("III", "Final Fantasy III"),
	IV("IV", "Final Fantasy IV"),
	V("V", "Final Fantasy V"),
	VI("VI", "Final Fantasy VI"),
	VII("VII", "Final Fantasy VII"),
	VIII("VIII", "Final Fantasy VIII"),
	IX("IX", "Final Fantasy IX"),
	X("X", "Final Fantasy X"),
	XI("XI", "Final Fantasy XI"),
	XII("XII", "Final Fantasy XII"),
	XIII("XIII", "Final Fantasy XIII"),
	XIV("XIV", "Final Fantasy XIV"),
	XV("XV", "Final Fantasy XV"),
	XVI("XVI", "Final Fantasy XVI"),
	TYPE0("Type-0", "Final Fantasy Type-0"),
	DFF("DFF", "Dissidia Final Fantasy"),
	FFCC("FFCC", "Final Fantasy Crystal Chronicles"),
	MOBIUS("MOBIUS", "Mobius Final Fantasy"),
	FFT("FFT", "Final Fantasy Tactics"),
	FFTA("FFTA", "Final Fantasy Tactics Advance"),
	FFTA2("FFTA2", "Final Fantasy Tactics A2: Grimoire of the Rift"),
	FFBE("FFBE", "Final Fantasy Brave Exvius"),
	THEATRHYTHM("THEATRHYTHM", "Theatrhythm Final Fantasy"),
	PICTLOGICA("PICTLOGICA", "Pictlogica Final Fantasy"),
	FFL("FFL", "Final Fantasy Legends"),
	WOFF("WOFF", "World of Final Fantasy"),
	FFEX("FFEX", "Final Fantasy Explorers"),
	SPECIAL("Special", "Final Fantasy (Special)"), // no game associated
	LOV("LOV", "Lord of Vermilion"),
	CRYSTAL_HUNT("Crystal Hunt", "Chocobo's Crystal Hunt"),
	FFRK("FFRK", "Final Fantasy Record Keeper");
	
	private final String displayName;
	private final String fullName;
	
	Category(String displayName, String fullName) {
		this.displayName = displayName;
		this.fullName = fullName;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getFullName() {
		return fullName;
	}

}

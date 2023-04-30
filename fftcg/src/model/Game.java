package model;

public class Game {
	
	public Board board;
	public Player player1;
	public Player player2;
	public int turn;
	
	/**
	 * 50/50 chance
	 * @return the player who will go first
	 */
	public Player decideFirstPlayer() {
		if (Math.random() < .5) {
			return player1;
		} else {
			return player2;
		}
	}

}

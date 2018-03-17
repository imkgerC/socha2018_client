package sc.player2018.logic;

import java.util.ArrayList;
import sc.plugin2018.FieldType;
import sc.plugin2018.GameState;
import sc.plugin2018.Move;
import sc.plugin2018.Player;

public class MidGameLogic {
	/*
	 * Johannes' strategy for the start: Move past the first salad field but use it
	 * and waste a turn there (sometimes) Then lose all salads at the second salad
	 * field.
	 */

	private static int SALAD_FIELD = 22; // 22 is OUR salad field
	private static int FALLBACK_FIELD = 15;
	
	public static Move getTurn(GameState gameState) {
		Player currentPlayer = gameState.getCurrentPlayer();
		int currentIndex = currentPlayer.getFieldIndex();
		ArrayList<Move> possibleMoves = gameState.getPossibleMoves();
		// if we can eat a salad, we should
		Move returnMove = LogicHelper.getEatSalad(possibleMoves);
		if(returnMove != null) {
			return returnMove;
		}
		if (currentIndex >= 22) {
			// go back, you have salads to eat!
			returnMove = LogicHelper.getFallback(possibleMoves);
			if(returnMove != null) {
				return returnMove;
			}
		} else {
			if (!gameState.isOccupied(SALAD_FIELD)) {
				returnMove = LogicHelper.getNextByType(FieldType.SALAD, possibleMoves, gameState, currentIndex);
				if(returnMove != null) {
					return returnMove;
				}
				returnMove = LogicHelper.getNextHareCarrot(possibleMoves, gameState, currentIndex);
				if(returnMove != null) {
					return returnMove;
				}
			} else {
				if (currentIndex == FALLBACK_FIELD) {
					returnMove = LogicHelper.getNextByType(FieldType.POSITION_2, possibleMoves, gameState, currentIndex);
					if(returnMove != null) {
						return returnMove;
					}
				}
				returnMove = LogicHelper.getFallback(possibleMoves);
				if(returnMove != null) {
					return returnMove;
				}
				returnMove = LogicHelper.getNextAdvance(possibleMoves);
				if(returnMove != null) {
					return returnMove;
				}
			}
		}
		return null;
	}
}
